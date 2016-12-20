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

class MemoryStatusUpdateIntegrationTest extends AbstractIntegrationSpec {

    def "can register a listener for JVM and OS memory status update events"() {
        given:
        buildFile << waitForMemoryEventsTask()

        when:
        succeeds 'waitForMemoryEvents'

        then:
        result.output.contains jvmLogStatement()
        result.output.contains osLogStatement()
    }

    private static String waitForMemoryEventsTask() {
        return '''
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.TimeUnit
            import org.gradle.process.internal.health.memory.*

            task waitForMemoryEvents {
                doLast {
                    final CountDownLatch notification = new CountDownLatch(2)

                    MemoryManager manager = project.services.get(MemoryManager.class)
                    JvmMemoryStatusListener jvmMemoryStatusListener = new JvmMemoryStatusListener() {
                        void onJvmMemoryStatus(JvmMemoryStatus memoryStatus) {
                            logger.lifecycle "JVM MemoryStatus notification: $memoryStatus"
                            notification.countDown()
                        }
                    };
                    OsMemoryStatusListener osMemoryStatusListener = new OsMemoryStatusListener() {
                        void onOsMemoryStatus(OsMemoryStatus memoryStatus) {
                            logger.lifecycle "OS MemoryStatus notification: $memoryStatus"
                            notification.countDown()
                        }
                    };

                    try {
                        manager.addListener(jvmMemoryStatusListener)
                        manager.addListener(osMemoryStatusListener)
                        logger.warn "Waiting for memory status events..."

                        notification.await(20, TimeUnit.SECONDS)
                    } finally {
                        manager.removeListener(osMemoryStatusListener)
                        manager.removeListener(jvmMemoryStatusListener)
                    }
                }
            }
        '''.stripIndent()
    }

    private static String jvmLogStatement() {
        return 'JVM MemoryStatus notification'
    }

    private static String osLogStatement() {
        return 'OS MemoryStatus notification'
    }
}
