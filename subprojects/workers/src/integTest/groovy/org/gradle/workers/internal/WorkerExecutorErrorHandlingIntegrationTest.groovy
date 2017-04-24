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

import org.gradle.internal.jvm.Jvm
import spock.lang.Unroll

class WorkerExecutorErrorHandlingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Unroll
    def "produces a sensible error when there is a failure in the worker runnable in #forkMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableThatFails

            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = RunnableThatFails.class
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")

        and:
        failureHasCause("Failure from runnable")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    def "produces a sensible error when there is a failure starting a worker daemon"() {
        executer.withStackTraceChecksDisabled()
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                forkMode = ForkMode.ALWAYS
                additionalForkOptions = {
                    it.jvmArgs "-foo"
                }
            }
        """.stripIndent()

        when:
        fails("runInDaemon")

        then:
        errorOutput.contains(unrecognizedOptionError)

        and:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")

        and:
        failureHasCause("Failed to run Gradle Worker Daemon")
    }

    @Unroll
    def "produces a sensible error when a parameter can't be serialized to the worker in #forkMode"() {
        withRunnableClassInBuildSrc()
        withParameterMemberThatFailsSerialization()

        buildFile << """
            $alternateRunnable

            task runAgainInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = AlternateRunnable.class
            }
            
            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                foo = new FooWithUnserializableBar()
                finalizedBy runAgainInWorker
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")
        failureHasCause("Could not serialize parameters")
        failureHasCause("Broken")

        and:
        executedAndNotSkipped(":runAgainInWorker")
        assertRunnableExecuted("runAgainInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "produces a sensible error when a parameter can't be de-serialized in the worker in #forkMode"() {
        def parameterJar = file("parameter.jar")
        withRunnableClassInBuildSrc()
        withParameterMemberThatFailsDeserialization()

        buildFile << """  
            $alternateRunnable

            task runAgainInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = AlternateRunnable.class
            }

            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                additionalClasspath = files('${parameterJar.name}')
                foo = new FooWithUnserializableBar()
                finalizedBy runAgainInWorker
            }
        """

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")
        failureHasCause("Could not deserialize parameters")
        failureHasCause("Broken")

        and:
        executedAndNotSkipped(":runAgainInWorker")
        assertRunnableExecuted("runAgainInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "produces a sensible error even if the action failure cannot be fully serialized in #forkMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $alternateRunnable

            task runAgainInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = AlternateRunnable.class
            }

            $runnableThatThrowsUnserializableMemberException

            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = RunnableThatFails.class
                finalizedBy runAgainInWorker
            }
        """

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Unserializable exception from runnable")

        and:
        executedAndNotSkipped(":runAgainInWorker")
        assertRunnableExecuted("runAgainInWorker")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "produces a sensible error when the runnable cannot be instantiated in #forkMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableThatFailsInstantiation

            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = RunnableThatFails.class
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Could not create an instance of type RunnableThatFails.")
        failureHasCause("You shall not pass!")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "produces a sensible error when parameters are incorrect in #forkMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableWithDifferentConstructor

            task runInWorker(type: WorkerTask) {
                forkMode = $forkMode
                runnableClass = RunnableWithDifferentConstructor.class
            }
        """.stripIndent()

        when:
        fails("runInWorker")

        then:
        failureHasCause("A failure occurred while executing RunnableWithDifferentConstructor")
        failureHasCause("Could not create an instance of type RunnableWithDifferentConstructor.")
        failureHasCause("Too many parameters provided for constructor for class RunnableWithDifferentConstructor. Expected 2, received 3.")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    String getUnrecognizedOptionError() {
        def jvm = Jvm.current()
        if (jvm.ibmJvm) {
            return "Command-line option unrecognised: -foo"
        } else {
            return "Unrecognized option: -foo"
        }
    }

    String getRunnableThatFails() {
        return """
            public class RunnableThatFails implements Runnable {
                @javax.inject.Inject
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { }

                public void run() {
                    throw new RuntimeException("Failure from runnable");
                }
            }
        """
    }

    String getRunnableThatThrowsUnserializableMemberException() {
        return """
            public class RunnableThatFails implements Runnable {
                @javax.inject.Inject
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { }

                public void run() {
                    throw new UnserializableMemberException("Unserializable exception from runnable");
                }
                
                private class Bar { }
                
                private class UnserializableMemberException extends RuntimeException {
                    private Bar bar = new Bar();
                    
                    UnserializableMemberException(String message) {
                        super(message);
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

    String getRunnableThatFailsInstantiation() {
        return """
            public class RunnableThatFails implements Runnable {
                @javax.inject.Inject
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { 
                    throw new IllegalArgumentException("You shall not pass!")
                }

                public void run() {
                }
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

        addImportToBuildScript("org.gradle.other.FooWithUnserializableBar")
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

        addImportToBuildScript("org.gradle.other.FooWithUnserializableBar")
    }

    String getRunnableWithDifferentConstructor() {
        return """
            public class RunnableWithDifferentConstructor implements Runnable {
                @javax.inject.Inject
                public RunnableWithDifferentConstructor(List<String> files, File outputDir) { 
                }

                public void run() {
                }
            }
        """
    }
}
