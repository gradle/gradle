/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.workers.fixtures.WorkerExecutorFixture

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@IntegrationTestTimeout(120)
class WorkerExecutorErrorHandlingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    WorkerExecutorFixture.WorkActionClass actionThatFailsInstantiation
    WorkerExecutorFixture.WorkActionClass actionThatThrowsUnserializableMemberException

    def setup() {
        actionThatFailsInstantiation = fixture.getWorkActionThatCreatesFiles("ActionThatFailsInstantiation")
        actionThatFailsInstantiation.with {
            constructorAction = """
                throw new IllegalArgumentException("You shall not pass!");
            """
        }

        actionThatThrowsUnserializableMemberException = fixture.getWorkActionThatCreatesFiles("ActionThatFails")
        actionThatThrowsUnserializableMemberException.with {
            extraFields = """
                private class Bar { }

                private class UnserializableMemberException extends RuntimeException {
                    private Bar bar = new Bar();

                    UnserializableMemberException(String message) {
                        super(message);
                    }
                }
            """
            action = """
                throw new UnserializableMemberException("Unserializable exception during execution");
            """
        }
    }

    def "produces a sensible error when there is a failure in the worker runnable in #isolationMode"() {
        def failureExecution = fixture.workActionThatFails.writeToBuildFile()
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workActionClass = ${failureExecution.name}.class
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing ${failureExecution.name}")

        and:
        failureHasCause("Failure from work action")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "produces a sensible error when there is a failure in the worker runnable and work completes before the task in #isolationMode"() {
        def failureExecution = fixture.workActionThatFails.writeToBuildFile()
        fixture.withWorkActionClassInBuildSrc()

        buildFile << """
            $workerTaskThatWaits

            task runInWorker(type: WorkerTaskThatWaits) {
                isolationMode = $isolationMode
                workActionClass = ${failureExecution.name}.class
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing ${failureExecution.name}")

        and:
        failureHasCause("Failure from work action")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "produces a sensible error when there is a failure starting a worker daemon"() {
        executer.withStackTraceChecksDisabled()
        def workAction = fixture.workActionThatCreatesFiles.writeToBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = 'processIsolation'
                additionalForkOptions = {
                    it.jvmArgs "-foo"
                }
            }
        """.stripIndent()

        when:
        fails("runInDaemon")

        then:
        failure.assertHasErrorOutput(unrecognizedOptionError)

        and:
        failureHasCause("A failure occurred while executing ${workAction.packageName}.${workAction.name}")

        and:
        failureHasCause("Failed to run Gradle Worker Daemon")
    }

    @ToBeFixedForConfigurationCache(because = "non-serializable fails configuration cache store earlier")
    def "produces a sensible error when a parameter can't be serialized to the worker in #isolationMode"() {
        def workAction = fixture.workActionThatCreatesFiles.writeToBuildSrc()
        def alternateExecution = fixture.alternateWorkAction.writeToBuildSrc()
        withParameterMemberThatFailsSerialization()

        buildFile << """
            task runAgainInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workActionClass = ${alternateExecution.name}.class
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                foo = new FooWithUnserializableBar()
                finalizedBy runAgainInWorker
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing ${workAction.packageName}.${workAction.name}")
        failureHasCause("Could not isolate value ")
        failureHasCause("Could not serialize value of type FooWithUnserializableBar")

        and:
        executedAndNotSkipped(":runAgainInWorker")
        assertWorkerExecuted("runAgainInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = "Error output not as expected when configuration cache is enabled"
    )
    def "produces a sensible error when a parameter can't be de-serialized in the worker in #isolationMode"() {
        def parameterJar = file("parameter.jar")
        def workAction = fixture.workActionThatCreatesFiles.writeToBuildSrc()
        def alternateExecution = fixture.alternateWorkAction.writeToBuildSrc()
        withParameterMemberThatFailsDeserialization()

        buildFile << """
            task runAgainInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workActionClass = ${alternateExecution.name}.class
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                additionalClasspath = files('${parameterJar.name}')
                foo = new FooWithUnserializableBar()
                finalizedBy runAgainInWorker
            }
        """

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing ${workAction.packageName}.${workAction.name}")
        failureHasCause("Couldn't populate class org.gradle.other.FooWithUnserializableBar")

        and:
        executedAndNotSkipped(":runAgainInWorker")
        assertWorkerExecuted("runAgainInWorker")

        where:
        isolationMode << ["'classLoaderIsolation'", "'processIsolation'"]
    }

    def "produces a sensible error even if the action failure cannot be fully serialized in #isolationMode"() {
        fixture.withWorkActionClassInBuildSrc()
        def failureExecution = actionThatThrowsUnserializableMemberException.writeToBuildSrc()
        def alternateExecution = fixture.alternateWorkAction.writeToBuildSrc()

        buildFile << """
            task runAgainInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workActionClass = ${alternateExecution.name}.class
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workActionClass = ${failureExecution.name}.class
                finalizedBy runAgainInWorker
            }
        """

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing ${failureExecution.packageName}.${failureExecution.name}")
        failureHasCause("Unserializable exception during execution")

        and:
        executedAndNotSkipped(":runAgainInWorker")
        assertWorkerExecuted("runAgainInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "produces a sensible error when the runnable cannot be instantiated in #isolationMode"() {
        fixture.withWorkActionClassInBuildSrc()
        def failureExecution = actionThatFailsInstantiation.writeToBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                workActionClass = ${failureExecution.name}.class
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing ${failureExecution.packageName}.${failureExecution.name}")
        failureHasCause("Could not create an instance of type ${failureExecution.packageName}.${failureExecution.name}.")
        failureHasCause("You shall not pass!")

        where:
        isolationMode << ISOLATION_MODES
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "produces a sensible error when worker fails before logging is initialized"() {
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
                additionalForkOptions = {
                    it.systemProperty("org.gradle.native.dir", "/dev/null")
                }
            }
        """.stripIndent()

        when:
        executer.withStackTraceChecksDisabled()
        fails("runInWorker")

        then:
        result.assertHasErrorOutput("net.rubygrapefruit.platform.NativeException: Failed to load native library")
    }

    def "produces a sensible error when the work action is not implemented properly"() {
        fixture.withWorkActionClassInBuildScript()

        buildFile << """
            abstract class BadWorkAction implements WorkAction<WorkParameters> {
                @Inject
                BadWorkAction() { }

                void execute() { }
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = BadWorkAction.class
            }
        """.stripIndent()

        when:
        executer.withStackTraceChecksDisabled()
        fails("runInWorker")

        then:
        failure.assertHasCause("Could not create the parameters for BadWorkAction: must use a sub-type of WorkParameters as the parameters type. Use WorkParameters.None as the parameters type for implementations that do not take parameters.")
    }

    String getUnrecognizedOptionError() {
        def jvm = Jvm.current()
        if (jvm.ibmJvm) {
            return "Command-line option unrecognised: -foo"
        } else {
            return "Unrecognized option: -foo"
        }
    }

    String getWorkerTaskThatWaits() {
        return """
            public class WorkerTaskThatWaits extends WorkerTask {
                @TaskAction
                void executeTask() {
                    super.executeTask();
                    while (true) {
                        if (new File("\${outputFileDirPath}/finished").exists()) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                }
            }
        """
    }

    String getClassThatFailsDeserialization() {
        return """
            package org.gradle.other;

            import java.io.Serializable;
            import java.io.IOException;

            public class Bar implements Serializable {
                private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
                    throw new IOException("Broken");
                }
            }
        """
    }

    String getClassThatFailsSerialization() {
        return """
            package org.gradle.other;

            import java.io.Serializable;
            import java.io.IOException;

            public class Bar implements Serializable {
                private void writeObject(java.io.ObjectOutputStream out) throws IOException {
                    throw new IOException("Broken");
                }
            }
        """
    }

    String getParameterClassWithUnserializableMember() {
        return """
            package org.gradle.other;

            import java.io.Serializable;

            public class FooWithUnserializableBar extends Foo implements Serializable {
                private final Bar bar = new Bar();
            }
        """
    }

    void withParameterMemberThatFailsSerialization() {
        // Create an un-serializable class
        file('buildSrc/src/main/java/org/gradle/other/Bar.java').text = """
            $classThatFailsSerialization
        """

        // Create a Foo class with an un-serializable member
        file('buildSrc/src/main/java/org/gradle/other/FooWithUnserializableBar.java').text = """
            $parameterClassWithUnserializableMember
        """

        fixture.addImportToBuildScript("org.gradle.other.FooWithUnserializableBar")
    }

    void withParameterMemberThatFailsDeserialization() {
        // Overwrite the Foo class with a class with an un-serializable member
        file('buildSrc/src/main/java/org/gradle/other/FooWithUnserializableBar.java').text = """
            $parameterClassWithUnserializableMember
        """

        // An unserializable member class
        file('buildSrc/src/main/java/org/gradle/error/Bar.java').text = """
            $classThatFailsDeserialization
        """

        fixture.addImportToBuildScript("org.gradle.other.FooWithUnserializableBar")
    }
}
