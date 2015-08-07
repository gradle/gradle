Tasks created via the new rule infrastructure are not always being `realised` at a point in time where build authors can reliably reference and apply configuration (actions) to such
tasks. This has caused backwards compatibility issues with Gradle because rule-source created tasks referenced by type or with live collections (i.e. `tasks.withType(Foo)` and `tasks.matching { ..}`)
are not interpreted as being required during the configuration phase and are therefore not realised in time. Conversely, tasks __not__ created using the new rule infrastructure are always created
during the configuration phase.

This spec is about assuring that regardless of how a task was created, from a rule-source or otherwise, a build author can reliably reference and configure that task during the
configuration phase of a build.

# Stories

## Actions are not being applied to rule-source created tasks at configuration time.

### Implementation
  - when `.withType(Foo)` is used all tasks of type `Foo` will be realised before the `afterEvaluate` lifecycle phase and the action will be called during the configuration phase.
  - when `.matching()` is used all child nodes, of the model node representing the task container, will be realized before the `afterEvaluate` lifecycle phase and the action will
  be called during the configuration phase.. This is a performance hit and should be highlighted appropriately.


### Test coverage

- Verify that an action is applied to a rule-source task during configuration when the following constructs are used:
    1. `tasks.withType(type, action)`
    1. `tasks.withType(type).all(action)`
    1. `tasks.matching(predicate).all(action)`
- Rule sources can have _rules_ with tasks as the subject _after_ the use of the `.withType()` and `.matching()` constructs.
- When `tasks.withType(Foo)` is used, verify that the only realized rule generate tasks are of type `Foo`.
- Verify that actions are executed on a rule-source task of type `Foo`, at configuration, time under the following conditions for __both__ single and multi project builds:
    - `tasks.withType(Foo, action)` is used but nothing else references any tasks of type `Foo`
    - `tasks.withType(Foo, action)` is used but some task `bar`, `dependsOn tasks.withType(Foo)` and `bar` is executed.
    - A rule-source task of type `Foo` has configuration rules (`@Mutate`)
    - A rule-source task of type `Foo` doesn't have configuration rules (`@Mutate`)
    - A rule-source is applied _before_ the action is added to the task of type `Foo`
    - A rule-source is applied _after_ the action is added to the task of type `Foo`
    - A task crested with the model DSL `model { tasks.create(..) }`
    - A rule-source task's action uses `withType` or `matching` to apply an action to another task at execution time.

## Rule-source created tasks cannot be reliably iterated over.
### Implementation

### Test coverage
TBD
    - `tasks.withType(type).iterator()`
    - `tasks.matching(predicate).iterator()`




