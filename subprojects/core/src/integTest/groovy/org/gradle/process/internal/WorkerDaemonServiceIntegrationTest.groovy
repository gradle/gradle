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

import org.gradle.execution.taskgraph.DefaultTaskExecutionPlan
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Ignore


class WorkerDaemonServiceIntegrationTest extends AbstractIntegrationSpec {
    def outputFileDir = file("build/workerDaemons")
    def outputFileDirPath = TextUtil.normaliseFileSeparators(outputFileDir.absolutePath)
    def list = [ 1, 2, 3 ]
    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        buildFile << """
            $taskTypeUsingWorkerDaemon
        """
    }

    def "can create and use a daemon runnable defined in buildSrc"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

    def "can create and use a daemon runnable defined in build script"() {
        withRunnableClassInBuildScript()

        buildFile << """
            task runInDaemon(type: DaemonTask)
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

    def "can create and use a daemon runnable defined in an external jar"() {
        def runnableJarName = "runnable.jar"
        withRunnableClassInExternalJar(file(runnableJarName))

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("$runnableJarName")
                }
            }

            task runInDaemon(type: DaemonTask)
        """

        when:
        succeeds("runInDaemon")

        then:
        assertRunnableExecuted("runInDaemon")
    }

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

    def "re-uses an existing idle daemon" () {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)

            task reuseDaemon(type: DaemonTask) {
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "starts a new daemon when existing daemons are incompatible" () {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)

            task startNewDaemon(type: DaemonTask) {
                dependsOn runInDaemon

                // Force a new daemon to be used
                additionalForkOptions = {
                    it.systemProperty("foo", "bar")
                }
            }
        """

        when:
        succeeds("startNewDaemon")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "starts a new daemon when there are no idle compatible daemons available" () {
        blockingServer.start()
        blockingServer.expectConcurrentExecution("runInDaemon", "startNewDaemon")

        withRunnableClassInBuildSrc()
        withBlockingRunnableClassInBuildSrc("http://localhost:${blockingServer.port}")

        buildFile << """
            task runInDaemon(type: DaemonTask) {
                runnableClass = BlockingRunnable.class
            }

            task startNewDaemon(type: DaemonTask) {
                runnableClass = BlockingRunnable.class
            }

            task runAllDaemons {
                dependsOn runInDaemon, startNewDaemon
            }
        """

        when:
        args("--parallel", "-D${DefaultTaskExecutionPlan.INTRA_PROJECT_TOGGLE}=true")
        succeeds("runAllDaemons")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "re-uses an existing compatible daemon when a different runnable is executed" () {
        withRunnableClassInBuildSrc()
        withAlternateRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)

            task reuseDaemon(type: DaemonTask) {
                runnableClass = AlternateRunnable.class
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    @Ignore("Flaky")
    def "worker daemon lifecycle is logged" () {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: DaemonTask)
        """

        when:
        args("--info")
        succeeds("runInDaemon")

        then:
        output.contains("Starting process 'Gradle Worker Daemon 1'.")
        output.contains("Successfully started process 'Gradle Worker Daemon 1'")
        output.contains("Executing org.gradle.test.TestRunnable in worker daemon")
        output.contains("Successfully executed org.gradle.test.TestRunnable in worker daemon")
    }

    String getUnrecognizedOptionError() {
        def jvm = Jvm.current()
        if (jvm.ibmJvm) {
            return "Command-line option unrecognised: -foo"
        } else {
            return "Unrecognized option: -foo"
        }
    }

    void assertRunnableExecuted(String taskName) {
        list.each {
            outputFileDir.file(taskName).file(it).assertExists()
        }
    }

    void assertSameDaemonWasUsed(String task1, String task2) {
        list.each {
            assert outputFileDir.file(task1).file(it).text == outputFileDir.file(task2).file(it).text
        }
    }

    void assertDifferentDaemonsWereUsed(String task1, String task2) {
        list.each {
            assert outputFileDir.file(task1).file(it).text != outputFileDir.file(task2).file(it).text
        }
    }

    String getTaskTypeUsingWorkerDaemon() {
        withParameterClassInBuildSrc()

        return """
            import javax.inject.Inject
            import org.gradle.process.daemon.WorkerDaemonService
            import org.gradle.other.Foo

            @ParallelizableTask
            class DaemonTask extends DefaultTask {
                def list = $list
                def outputFileDirPath = "${outputFileDirPath}/\${name}"
                def additionalForkOptions = {}
                def runnableClass = TestRunnable.class

                @Inject
                WorkerDaemonService getWorkerDaemons() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    workerDaemons.daemonRunnable(runnableClass)
                        .forkOptions(additionalForkOptions)
                        .params(list.collect { it as String }, new File(outputFileDirPath), new Foo())
                        .execute()
                }
            }
        """
    }

    String getRunnableThatCreatesFiles() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.io.PrintWriter;
            import java.io.BufferedWriter;
            import java.io.FileWriter;
            import java.util.UUID;

            public class TestRunnable implements Runnable {
                private final List<String> files;
                protected final File outputDir;
                private final Foo foo;
                private static final String id = UUID.randomUUID().toString();

                public TestRunnable(List<String> files, File outputDir, Foo foo) {
                    this.files = files;
                    this.outputDir = outputDir;
                    this.foo = foo;
                }

                public void run() {
                    outputDir.mkdirs();

                    for (String name : files) {
                        PrintWriter out = null;
                        try {
                            File outputFile = new File(outputDir, name);
                            outputFile.createNewFile();
                            out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
                            out.print(id);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            if (out != null) {
                                out.close();
                            }
                        }
                    }
                }
            }
        """
    }

    String getBlockingRunnableThatCreatesFiles(String url) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class BlockingRunnable extends TestRunnable {
                public BlockingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    super.run();
                    try {
                        new URL("$url/" + outputDir.getName()).openConnection().getHeaderField("RESPONSE");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    String getAlternateRunnable() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;

            public class AlternateRunnable extends TestRunnable {
                public AlternateRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }
            }
        """
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

    void withParameterClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/other/Foo.java") << """
            package org.gradle.other;

            import java.io.Serializable;

            public class Foo implements Serializable { }
        """
    }

    void withRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/TestRunnable.java") << """
            package org.gradle.test;

            $runnableThatCreatesFiles
        """

        addImportToBuildScript("org.gradle.test.TestRunnable")
    }

    void withBlockingRunnableClassInBuildSrc(String url) {
        file("buildSrc/src/main/java/org/gradle/test/BlockingRunnable.java") << """
            package org.gradle.test;

            ${getBlockingRunnableThatCreatesFiles(url)}
        """

        addImportToBuildScript("org.gradle.test.BlockingRunnable")
    }

    void withAlternateRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/AlternateRunnable.java") << """
            package org.gradle.test;

            $alternateRunnable
        """

        addImportToBuildScript("org.gradle.test.AlternateRunnable")
    }

    void withRunnableClassInBuildScript() {
        buildFile << """
            $runnableThatCreatesFiles
        """
    }

    void withRunnableClassInExternalJar(File runnableJar) {
        file("buildSrc").deleteDir()

        def builder = artifactBuilder()
        builder.sourceFile("org/gradle/test/TestRunnable.java") << """
            package org.gradle.test;

            $runnableThatCreatesFiles
        """
        builder.sourceFile("org/gradle/other/Foo.java") << """
            package org.gradle.other;

            import java.io.Serializable;

            public class Foo implements Serializable { }
        """
        builder.buildJar(runnableJar)

        addImportToBuildScript("org.gradle.test.TestRunnable")
    }

    void addImportToBuildScript(String className) {
        buildFile.text = """
            import ${className}
            ${buildFile.text}
        """
    }
}
