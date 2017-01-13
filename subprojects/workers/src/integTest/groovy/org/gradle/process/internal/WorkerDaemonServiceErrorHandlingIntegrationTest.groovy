/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.internal.jvm.Jvm

class WorkerDaemonServiceErrorHandlingIntegrationTest extends AbstractWorkerDaemonServiceIntegrationTest {
    def "produces a sensible error when there is a failure in the daemon runnable"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableThatFails

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableThatFails.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")

        and:
        failureHasCause("Failure from runnable")

        and:
        errorOutput.contains("Caused by: java.lang.RuntimeException: Failure from runnable")
    }

    def "produces a sensible error when there is a failure starting a daemon"() {
        executer.withStackTraceChecksDisabled()
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask) {
                additionalForkOptions = {
                    it.jvmArgs "-foo"
                }
            }
        """

        when:
        fails("runInDaemon")

        then:
        errorOutput.contains(unrecognizedOptionError)

        and:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")

        and:
        failureHasCause("Failed to run Gradle Worker Daemon")
    }

    def "produces a sensible error when a parameter can't be serialized"() {
        withRunnableClassInBuildSrc()
        withUnserializableParameterInBuildSrc()

        buildFile << """
            $alternateRunnable

            task runAgainInDaemon(type: DaemonTask) {
                runnableClass = AlternateRunnable.class
            }
            
            task runInDaemon(type: DaemonTask) {
                foo = new FooWithUnserializableBar()
                finalizedBy runAgainInDaemon
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")
        failureCauseContains("Could not write message")
        errorOutput.contains("Caused by: java.io.NotSerializableException: org.gradle.error.Bar")

        and:
        executedAndNotSkipped(":runAgainInDaemon")
        assertRunnableExecuted("runAgainInDaemon")
    }

    def "produces a sensible error when a parameter can't be de-serialized in the worker"() {
        def parameterJar = file("parameter.jar")
        withRunnableClassInBuildSrc()
        withUnserializableParameterMemberInExternalJar(parameterJar)

        buildFile << """  
            $alternateRunnable

            task runAgainInDaemon(type: DaemonTask) {
                runnableClass = AlternateRunnable.class
            }

            task runInDaemon(type: DaemonTask) {
                additionalClasspath = files('${parameterJar.name}')
                foo = new FooWithUnserializableBar()
                finalizedBy runAgainInDaemon
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing org.gradle.test.TestRunnable")
        failureCauseContains("Could not read message")
        errorOutput.contains("Caused by: java.lang.ClassNotFoundException: org.gradle.error.Bar")

        and:
        executedAndNotSkipped(":runAgainInDaemon")
        assertRunnableExecuted("runAgainInDaemon")
    }

    def "produces a sensible error even if the action failure cannot be fully serialized"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $alternateRunnable

            task runAgainInDaemon(type: DaemonTask) {
                runnableClass = AlternateRunnable.class
            }

            $runnableThatThrowsUnserializableMemberException

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableThatFails.class
                finalizedBy runAgainInDaemon
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Unserializable exception from runnable")

        and:
        executedAndNotSkipped(":runAgainInDaemon")
        assertRunnableExecuted("runAgainInDaemon")
    }

    def "produces a sensible error when the runnable cannot be instantiated"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableThatFailsInstantiation

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableThatFails.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("You shall not pass!")
    }

    def "produces a sensible error when parameters are incorrect"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            $runnableWithDifferentConstructor

            task runInDaemon(type: DaemonTask) {
                runnableClass = RunnableWithDifferentConstructor.class
            }
        """

        when:
        fails("runInDaemon")

        then:
        failureHasCause("A failure occurred while executing RunnableWithDifferentConstructor")
        failureHasCause("Could not find any public constructor for class RunnableWithDifferentConstructor which accepts parameters")
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

    String getUnserializableClass() {
        return """
            package org.gradle.error;
            
            public class Bar {
            }
        """
    }

    String getParameterClassWithUnserializableMember() {
        return """
            package org.gradle.other;
            
            import org.gradle.error.Bar;
            import java.io.Serializable;
            
            public class FooWithUnserializableBar extends Foo implements Serializable {
                private final Bar bar = new Bar();
            }
        """
    }

    String getRunnableThatFailsInstantiation() {
        return """
            public class RunnableThatFails implements Runnable {
                public RunnableThatFails(List<String> files, File outputDir, Foo foo) { 
                    throw new IllegalArgumentException("You shall not pass!")
                }

                public void run() {
                }
            }
        """
    }

    void withUnserializableParameterInBuildSrc() {
        // Create an un-serializable class
        file('buildSrc/src/main/java/org/gradle/error/Bar.java').text = """
            $unserializableClass
        """

        // Create a Foo class with an un-serializable member
        file('buildSrc/src/main/java/org/gradle/other/FooWithUnserializableBar.java').text = """
            $parameterClassWithUnserializableMember
        """

        addImportToBuildScript("org.gradle.other.FooWithUnserializableBar")
    }

    void withUnserializableParameterMemberInExternalJar(File parameterJar) {
        def builder = artifactBuilder()

        builder.sourceFile("org/gradle/error/Bar.java") << """
            $unserializableClass
        """

        // Overwrite the Foo class with a class with an un-serializable member
        file('buildSrc/src/main/java/org/gradle/other/FooWithUnserializableBar.java').text = """
            $parameterClassWithUnserializableMember
        """

        // A serializable form of the class so we can get past sending the message
        file('buildSrc/src/main/java/org/gradle/error/Bar.java').text = """
            package org.gradle.error;
            
            import java.io.Serializable;
            
            public class Bar implements Serializable {
            }
        """

        builder.buildJar(parameterJar)
        addImportToBuildScript("org.gradle.other.FooWithUnserializableBar")
    }

    String getRunnableWithDifferentConstructor() {
        return """
            public class RunnableWithDifferentConstructor implements Runnable {
                public RunnableWithDifferentConstructor(List<String> files, File outputDir) { 
                }

                public void run() {
                }
            }
        """
    }
}
