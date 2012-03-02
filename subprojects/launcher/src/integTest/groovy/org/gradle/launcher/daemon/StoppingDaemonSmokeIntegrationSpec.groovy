/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Timeout
import static org.gradle.integtests.fixtures.GradleDistributionExecuter.Executer.daemon

/**
 * by Szczepan Faber, created at: 1/20/12
 */
class StoppingDaemonSmokeIntegrationSpec extends DaemonIntegrationSpec {
    def concurrent = new ConcurrentTestUtil(120000)
    @Rule TemporaryFolder temp = new TemporaryFolder()

    @Timeout(300)
    def "does not deadlock when multiple stop requests are sent"() {
        given: "multiple daemons started"
        3.times { idx ->
            concurrent.start {
                runBuild("build$idx")
            }
        }
        concurrent.finished()
        
        when: "multiple stop requests are issued"
        5.times { idx ->
            concurrent.start {
                stopDaemon("stop$idx")
            }
        }
        concurrent.finished()

        then:
        def out = stopDaemon("stop").output
        out.contains(DaemonMessages.NO_DAEMONS_RUNNING)

        cleanup: "just in case"
        stopDaemon("cleanup")
    }

    //using separate dist/executer so that we can run them concurrently
    ExecutionResult stopDaemon(dirName) {
        def dist = new GradleDistribution()
        def dir = dist.file(dirName).createDir()
        return new GradleDistributionExecuter(daemon, dist).withDaemonBaseDir(temp.testDir).inDirectory(dir)
                .withArguments("-Dorg.gradle.jvmargs=", "--stop", "--info").run()
    }

    ExecutionResult runBuild(idx) {
        def dist = new GradleDistribution()
        def dir = dist.file("dir$idx").createDir()
        return new GradleDistributionExecuter(daemon, dist).withDaemonBaseDir(temp.testDir).inDirectory(dir)
                .withArguments("-Dorg.gradle.jvmargs=", "help", "--info").run()
    }
}
