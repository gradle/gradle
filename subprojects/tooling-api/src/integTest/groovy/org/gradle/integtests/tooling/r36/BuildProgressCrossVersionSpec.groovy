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

package org.gradle.integtests.tooling.r36

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=3.6")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {

    def "generates events for worker actions executed in-process and forked"() {
        given:
        settingsFile << "rootProject.name = 'single'"
        buildFile << """
        import org.gradle.workers.*
        class TestRunnable implements Runnable {
            @Override public void run() {
                // Do nothing
            }
        }
        task runInProcess {
            doLast {
                def workerExecutor = gradle.services.get(WorkerExecutor)
                workerExecutor.submit(TestRunnable) { config ->
                    config.forkMode = ForkMode.NEVER
                    config.displayName = 'My in-process worker action'
                }
            }
        }
        task runForked {
            doLast {
                def workerExecutor = gradle.services.get(WorkerExecutor)
                workerExecutor.submit(TestRunnable) { config ->
                    config.forkMode = ForkMode.ALWAYS
                    config.displayName = 'My forked worker action'
                }
            }
        }
    """.stripIndent()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('runInProcess', 'runForked')
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()

        and:
        events.operation('Task :runInProcess').descendant('My in-process worker action')
        events.operation('Task :runForked').descendant('My forked worker action')
    }
}
