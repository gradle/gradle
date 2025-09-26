# Configuration Cache

## Configuration Cache modes

CC can function in two modes:

- normal mode aka fail-on-problems
- warning mode aka lenient

Normal mode is how CC functions by default when enabled, ensuring safety and reliability.
Warning mode is an opt-in adoption and troubleshooting aid, meant to be used only temporarily.

## Problems and build failures

This section gives an overview of how CC problems interact with the build lifecycle,
when CC checks can result in build failures and how those are reported.

Whenever a problem is reported, it can be

1. **deferred** until the end of the build
2. **interrupting** the current scope of work
3. **suppressed** in an incompatible task
4. **suppressed silently** via graceful degradation

The way each problem is assigned severity depends on the CC mode and the context in which the problem appears.
See [`ProblemSeverity`](../../../platforms/core-configuration/configuration-problems-base/src/main/kotlin/org/gradle/internal/cc/impl/problems/ProblemSeverity.kt) for details.

All problems, regardless of their severity, are included in the CC report.
Except silently suppressed problems, all other problems can also appear in the CC summary printed in the console output.

### Deferred problems

Some problems are *deferred* to maximize the number of reported problems,
providing the user with a more comprehensive overview of what needs fixing before CC can function successfully.
This approach is similar to how compilers show all possible compilation problems, instead of only the first one encountered.

Most deferrable problems happen during task serialization, when CC writes the work graph to disk.
When some state referenced by a task is not supported by CC, that problem is reported but not acted on immediately.
Instead, the serialization proceeds to find potentially more problems with the rest of the task state.
Similarly, even if such problems were detected in one task, CC proceeds to serialize the rest of the work graph to collect all such problems.
Examples of unsupported state include platform types like `java.lang.Thread` or Gradle types like `Project`.
See [`UnsupportedTypesCodecs.kt`](../../../platforms/core-configuration/core-serialization-codecs/src/main/kotlin/org/gradle/internal/serialize/codecs/core/UnsupportedTypesCodecs.kt) for more details.

Many problems that appear during configuration time before CC-store are also deferrable.
For instance, we detect when user code starts an [external process](../../../subprojects/core/src/main/java/org/gradle/api/internal/ExternalProcessStartedListener.java)
or uses an [unsupported build listener](https://github.com/gradle/gradle/blob/89da055f53cfe9be784f616abf0dfa0f4a3ef065/platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/ConfigurationCacheState.kt#L937-L949).

This list is non-exhaustive, follow the calls to [`ProblemsListener.onProblem()`](https://github.com/gradle/gradle/blob/71e0b98ed84a392933c54a14a817e9609acd0d6f/platforms/core-configuration/configuration-problems-base/src/main/kotlin/org/gradle/internal/configuration/problems/ProblemsListener.kt#L24) to discover the rest.

When deferred problems are present, CC can fail the build in two points in time:

1. After the CC store has completed
2. After the build has finished

At each of these points, if _any_ deferred problems have been reported,
we fail the build with an _aggregated build failure_ (see `ConfigurationCacheProblemsException`).

Deferred problems appear in the CC console summary and in the CC report.

### Interrupting problems

Sometimes, it is impossible or unsafe to defer a problem and the current work must be immediately *interrupted*.

Interrupting problems can occur during work graph serialization (CC store) or deserialization (CC load).
One example is when `java.io.Serializable` throws an exception from `writeReplace()` or `readResolve()`.
Another is when a concurrency problem results in serializing a collection that is being modified as it is being written to disk.
In such cases, the CC store or load halts, and we fail the build with `ConfigurationCacheError`.
The build does not proceed to the execution phase.

There can be CC problems during the execution phase as well, even if store and load were successful.
For instance, a task action can call forbidden APIs, such as `Task.getProject()`, or
reach for project state via [script](../../../subprojects/core/src/main/java/org/gradle/groovy/scripts/BasicScript.java) object instance
(see `BrokenScript` in [GroovyCodecs.kt](../../../platforms/core-configuration/core-serialization-codecs/src/main/kotlin/org/gradle/internal/serialize/codecs/core/GroovyCodecs.kt)).
On a CC hit, only CC load is performed, so the project state would not have been initialized before such access.
Therefore, we prevent user logic from working with bad state by interrupting the task.
Failing the task also ensures that its results will not be cached.

Since an interrupting problem needs to fail the build immediately, each such problem results in a separate build failure.
However, all such problems are still included in the CC report, because they are caused by the CC constraints.
They also appear in the CC console summary, but with lower priority than deferred problems.

Note that even if an interrupting problem is present, the end-of-build CC checks are still executed,
and they still can result in an _aggregated build failure_ if there were any deferred problems.
However, the aggregated build failure will not be triggered by the interrupting problems alone.

### Suppressed problems

Incompatible tasks are still serialized by CC to capture remaining problems that need fixing before the incompatibility can be resolved.
These problems are classified as _suppressed_ and never result in build failure.

Suppressed problems appear in the CC console summary and in the CC report.

### Silently suppressed problems

Some Gradle features and tasks provided by the core plugins are not yet compatible with CC.
Since these are impossible for the user to fix, when their usage is detected, CC gracefully degrates to vintage,
while the usages are recorded as _silently suppressed_ problems.

These problems don't appear in the CC console summary but appear in the CC report.

### Problems and warning mode

The warning mode or lenient mode alters the way problems are handled by CC.
All interrupting problems are downgraded to deferred problems,
and the presence of deferred problems does not result in the _aggregated build failure_.

However, when CC runs in the warning mode, it can result in the
[`TooManyConfigurationCacheProblemsException`](../../../platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/ConfigurationCacheException.kt)
build failure, when the number of observed problems crosses a configurable threshold.
