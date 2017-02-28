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

import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule
import spock.lang.Unroll

class WorkerExecutorLoggingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    // We check the output in this test asynchronously because sometimes the logging output arrives
    // after the build finishes and we get a false negative
    @Unroll
    def "worker lifecycle is logged in #forkMode"() {
        def runnableJarName = "runnable.jar"
        withRunnableClassInExternalJar(file(runnableJarName))

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("$runnableJarName")
                }
            }

            task runInWorker(type: DaemonTask) {
                forkMode = $forkMode
            }

            task block {
                dependsOn runInWorker
                doLast {
                    $blockUntilReleased
                }
            }
        """.stripIndent()

        when:
        args("--info")
        def gradle = executer.withTasks("block").start()

        then:
        server.waitFor()

        and:
        if (forkMode == 'ForkMode.ALWAYS') {
            waitForAllOutput(gradle) {
                outputShouldContain("Starting process 'Gradle Worker Daemon 1'.")
                outputShouldContain("Successfully started process 'Gradle Worker Daemon 1'")
                outputShouldContain("Executing org.gradle.test.TestRunnable in worker daemon")
                outputShouldContain("Successfully executed org.gradle.test.TestRunnable in worker daemon")
            }
        } else {
            waitForAllOutput(gradle) {
                outputShouldContain("Executing org.gradle.test.TestRunnable in in-process worker")
                outputShouldContain("Successfully executed org.gradle.test.TestRunnable in in-process worker")
            }
        }

        when:
        server.release()

        then:
        gradle.waitForFinish()

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    @Unroll
    def "stdout, stderr and logging output of worker is redirected in #forkMode"() {

        buildFile << """
            ${runnableWithLogging}
            task runInDaemon(type: DaemonTask) {
                forkMode = $forkMode
            }
        """.stripIndent()

        when:
        succeeds("runInDaemon")

        then:
        output.contains("stdout message")
        output.contains("warn message")
        errorOutput.contains("error message")
        !output.contains("debug message")

        where:
        forkMode << ['ForkMode.ALWAYS', 'ForkMode.NEVER']
    }

    String getBlockUntilReleased() {
        return "new URL('${server.uri}').text"
    }

    String getRunnableWithLogging() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.util.UUID;
            import org.gradle.api.logging.Logging;

            public class TestRunnable implements Runnable {
                public TestRunnable(List<String> files, File outputDir, Foo foo) {
                }

                public void run() {
                    Logging.getLogger(getClass()).warn("warn message");
                    Logging.getLogger(getClass()).debug("debug message");
                    Logging.getLogger(getClass()).error("error message");
                    System.out.println("stdout message");
                    System.err.println("stderr message");
                }
            }
        """
    }

    boolean waitForAllOutput(GradleHandle gradle, Closure closure) {
        def watcher = new OutputWatcher(gradle)
        watcher.with(closure)
        return watcher.waitForAllOutputToBeSeen()
    }

    private static class OutputWatcher {
        final GradleHandle gradle
        final List<String> assertions = Lists.newArrayList()

        OutputWatcher(GradleHandle gradle) {
            this.gradle = gradle
        }

        void outputShouldContain(String output) {
            assertions.add(output)
        }

        void assertAllOutputIsSeen() {
            Iterator itr = assertions.iterator()
            while (itr.hasNext()) {
                String assertion = itr.next()
                if (gradle.standardOutput.contains(assertion)) {
                    itr.remove()
                } else {
                    throw new AssertionError("String '$assertion' not present in the build output")
                }
            }
        }

        boolean waitForAllOutputToBeSeen() {
            ConcurrentTestUtil.poll {
                assertAllOutputIsSeen()
            }
            return true
        }
    }
}
