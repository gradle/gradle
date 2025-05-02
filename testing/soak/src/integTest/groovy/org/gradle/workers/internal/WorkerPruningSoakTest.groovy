/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.workers.fixtures.WorkerExecutorFixture

/**
 * Verifies that we can spawn large amounts of workers without running out of memory, because the old ones will be killed
 * if necessary.
 *
 * <p>
 *     This mostly is a test for Windows, as other OSes have effectively-unlimited virtual memory, and won't need to kill
 *     processes.
 * </p>
 */
class WorkerPruningSoakTest extends AbstractIntegrationSpec {
    def fixture = new WorkerExecutorFixture(temporaryFolder)

    def setup() {
        fixture.prepareTaskTypeUsingWorker()

        // Purposefully not using an isolated daemon, this test should work regardless of any other builds before or after
        // If it doesn't, that indicates an issue with our worker memory management.

        // Enough to use up a large amount of memory on my 64GB Linux machine
        def workerCount = 200

        singleProjectBuild("root") {
            fixture.withAlternateWorkActionClassInBuildSrc()
            buildFile << """
            def buildCounter = project.property('counter')
            def runAllInWorkers = tasks.register("runAllInWorkers")
            for (int i = 0; i < $workerCount; i++) {
                def projectCounter = i
                def runInWorker = tasks.register("runInWorker" + i, WorkerTask) {
                    isolationMode = 'processIsolation'
                    workActionClass = AlternateWorkAction.class
                    additionalForkOptions = {
                        jvmArgs += ['-DprojectCounter=' + projectCounter, '-DbuildCounter=' + buildCounter]
                    }
                }
                runAllInWorkers.configure { dependsOn(runInWorker) }
            }
            """
        }
    }

    def "can re-run many times in a row with a changing large set of workers"() {
        expect:
        10.times {
            println("Run $it")
            succeeds("runAllInWorkers", "-Pcounter=" + it)
        }
    }

    // TODO add a version with persistent workers when we re-enable them in public API
}
