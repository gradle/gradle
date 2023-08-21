# Cross Version Test

## Pitfalls

###  Executing Gradle Versions that don't support your current JVM

This will result in e.g. 

    execution failed for task :tooling-api:gradle3.5.1CrossVersionTest'.
    > No tests found for given includes: [org.gradle.integtests.tooling.r81.LogLevelConfigCrossVersionSpec](--tests filter)`

You can fix it by adding `-PtestJavaVersion=8` to the build.

This is due to the fact that Gradle 4.6 and older don't support Java 11 and newer, which is checked before the test filter is applied.
`org.gradle.integtests.fixtures.executer.DefaultGradleDistribution.worksWith(org.gradle.internal.jvm.Jvm)` is the method that checks this.
This is used by `AbstractCompatibilityTestInterceptor` to filter out incompatible Gradle Versions.
Also all TestPrecondition annotations are checked in an interceptor (e.g. TestPrecondition.NOT_MAC_OS_X). 
The preconditions will also end up in the same message if it filters all tests.

### Executing with Kotlin scripts on Gradle Version prior 5.0.
    
This will result in an invocation as if no script is present and you'll be puzzled why nothing you wrote in the script is happening.
The reason is that Kotlin DSL was introduced in Gradle 5.0. Older Gradle versions simply ignore Kotlin scripts.
The solution is to use a `groovy` script since it works on all Gradle Versions. 


# Adding new progress event

* project `build-events`
  * `org.gradle.internal.build.event.types` 
     * `public class Default*Event extends AbstractProgressEvent<Internal*Descriptor> implements Serializable, Internal*Event`
     * `public class Default*Descriptor implements Serializable, Internal*Descriptor`
* project `tooling-api`
  * `org.gradle.tooling.internal.protocol`
     * `public interface Internal*Event extends InternalProgressEvent`
  * `org.gradle.tooling.internal.protocol.events`
    * `public interface Internal*Descriptor extends InternalOperationDescriptor`
  * `org.gradle.tooling.events.problems`
    * `public interface *Descriptor extends OperationDescriptor`
    * `public interface *Event extends ProgressEvent`
  * `org.gradle.tooling.events.problems.internal`
    * `public class Default*Event implements *Event`
    * `public class Default*OperationDescriptor extends DefaultOperationDescriptor implements *Descriptor`
