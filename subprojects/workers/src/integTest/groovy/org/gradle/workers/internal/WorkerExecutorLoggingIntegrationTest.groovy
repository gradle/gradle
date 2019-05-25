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

import spock.lang.Timeout
import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@Timeout(120)
class WorkerExecutorLoggingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Unroll
    def "worker lifecycle is logged in #isolationMode"() {
        fixture.withRunnableClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """.stripIndent()

        when:
        args("--debug")
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.contains("Build operation 'org.gradle.test.TestRunnable' started")
        gradle.standardOutput.contains("Build operation 'org.gradle.test.TestRunnable' completed")

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "stdout, stderr and logging output of worker is redirected in #isolationMode"() {
        buildFile << """
            ${runnableWithLogging}
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """.stripIndent()

        when:
        succeeds("runInWorker")

        then:
        output.contains("stdout message")
        output.contains("warn message")
        output.contains("slf4j warn")
        output.contains("jul warn")

        and:
        result.assertHasErrorOutput("stderr message")
        result.assertHasErrorOutput("error message")
        result.assertHasErrorOutput("slf4j error")
        result.assertHasErrorOutput("jul error")

        and:
        result.assertNotOutput("debug")

        where:
        isolationMode << ISOLATION_MODES
    }

    String getRunnableWithLogging() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.util.UUID;
            import org.gradle.api.logging.Logging;
            import org.slf4j.LoggerFactory;
            import javax.inject.Inject;

            public class TestRunnable implements Runnable {
                @Inject
                public TestRunnable(List<String> files, File outputDir, Foo foo) {
                }

                public void run() {
                    Logging.getLogger(getClass()).warn("warn message");
                    Logging.getLogger(getClass()).debug("debug message");
                    Logging.getLogger(getClass()).error("error message");
                    LoggerFactory.getLogger(getClass()).warn("slf4j warn");
                    LoggerFactory.getLogger(getClass()).debug("slf4j debug");
                    LoggerFactory.getLogger(getClass()).error("slf4j error");
                    java.util.logging.Logger.getLogger("worker").warning("jul warn");
                    java.util.logging.Logger.getLogger("worker").fine("jul debug");
                    java.util.logging.Logger.getLogger("worker").severe("jul error");
                    System.out.println("stdout message");
                    System.err.println("stderr message");
                }
            }
        """.stripIndent()
    }
}
