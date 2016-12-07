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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class WorkerDaemonServiceIntegrationTest extends AbstractWorkerDaemonServiceIntegrationTest {

    @Rule public final BlockingHttpServer blockingServer = new BlockingHttpServer()

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

    def "throws if used from a thread with no current build operation"() {
        given:
        withRunnableClassInBuildSrc()

        and:
        buildFile << '''
            class DaemonTaskUsingCustomThreads extends DaemonTask {
                @TaskAction
                void executeTask() {
                    def thrown = null
                    def customThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                workerDaemons.daemonRunnable(runnableClass)
                                    .forkOptions(additionalForkOptions)
                                    .params(list.collect { it as String }, new File(outputFileDirPath), new Foo())
                                    .execute()
                            } catch(Exception ex) {
                                thrown = ex
                            }
                        }
                    })
                    customThread.start()
                    customThread.join()
                    if(thrown) {
                        throw thrown
                    }
                }
            }

            task runInDaemon(type: DaemonTaskUsingCustomThreads)
        '''.stripIndent()

        when:
        fails 'runInDaemon'

        then:
        failure.assertHasCause 'No build operation associated with the current thread'
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
}
