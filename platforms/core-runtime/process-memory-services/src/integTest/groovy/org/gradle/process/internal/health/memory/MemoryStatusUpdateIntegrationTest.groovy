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

package org.gradle.process.internal.health.memory

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout

class MemoryStatusUpdateIntegrationTest extends AbstractIntegrationSpec {

    @IntegrationTestTimeout(60)
    def "can register a listener for JVM and OS memory status update events"() {
        given:
        buildFile << waitForMemoryEventsTask()

        when:
        succeeds 'waitForMemoryEvents'

        then:
        file("jvmReceived").exists()
        file("osReceived").exists()
    }

    private String waitForMemoryEventsTask() {
        return buildScriptSnippet('''
            import java.util.concurrent.CountDownLatch
            import org.gradle.process.internal.health.memory.*

            task waitForMemoryEvents {
                def projectDir = project.layout.projectDirectory
                def logger = logger
                doLast {
                    final CountDownLatch osNotification = new CountDownLatch(1)
                    final CountDownLatch jvmNotification = new CountDownLatch(1)

                    MemoryManager manager = services.get(MemoryManager.class)
                    manager.addListener(new JvmMemoryStatusListener() {
                        void onJvmMemoryStatus(JvmMemoryStatus memoryStatus) {
                            logger.lifecycle "JVM MemoryStatus notification: $memoryStatus"
                            projectDir.file("jvmReceived").asFile.createNewFile()
                            jvmNotification.countDown()
                        }
                    })
                    manager.addListener(new OsMemoryStatusListener() {
                        void onOsMemoryStatus(OsMemoryStatus memoryStatus) {
                            logger.lifecycle "OS MemoryStatus notification: $memoryStatus"
                            projectDir.file("osReceived").asFile.createNewFile()
                            osNotification.countDown()
                        }
                    })
                    logger.warn "Waiting for memory status events..."
                    osNotification.await()
                    jvmNotification.await()
                }
            }
        ''').stripIndent()
    }
}
