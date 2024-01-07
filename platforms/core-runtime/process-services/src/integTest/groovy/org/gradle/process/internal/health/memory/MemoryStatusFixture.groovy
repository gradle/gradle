/*
 * Copyright 2024 the original author or authors.
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

trait MemoryStatusFixture {
    static String waitForMemoryEventsTask() {
        return groovyScript('''
            import java.util.concurrent.CountDownLatch
            import org.gradle.process.internal.health.memory.*

            task waitForMemoryEvents {
                def projectDir = project.layout.projectDirectory
                def logger = logger
                doLast {
                    final CountDownLatch osNotification = new CountDownLatch(1)
                    final CountDownLatch jvmNotification = new CountDownLatch(1)

                    MemoryManager manager = services.get(MemoryManager.class)
                    def jvmListener = new JvmMemoryStatusListener() {
                        void onJvmMemoryStatus(JvmMemoryStatus memoryStatus) {
                            projectDir.file("jvmReceived").asFile.createNewFile()
                            jvmNotification.countDown()
                            logger.lifecycle "JVM MemoryStatus notification: $memoryStatus"
                        }
                    }

                    def osListener = new OsMemoryStatusListener() {
                        void onOsMemoryStatus(OsMemoryStatus memoryStatus) {
                            projectDir.file("osReceived").asFile.createNewFile()
                            osNotification.countDown()
                            logger.lifecycle "OS MemoryStatus notification: $memoryStatus"
                        }
                    }

                    manager.addListener(jvmListener)
                    manager.addListener(osListener)

                    logger.warn "Waiting for memory status events..."
                    osNotification.await()
                    jvmNotification.await()

                    manager.removeListener(jvmListener)
                    manager.removeListener(osListener)
                }
            }
        ''').stripIndent()
    }
}
