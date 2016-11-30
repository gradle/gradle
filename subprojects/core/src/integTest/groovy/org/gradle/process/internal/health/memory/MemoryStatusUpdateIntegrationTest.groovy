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

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Timeout

class MemoryStatusUpdateIntegrationTest extends DaemonIntegrationSpec {
    @Timeout(20)
    def "can register a listener for memory status update events"() {
        buildFile << '''
            import java.util.concurrent.CountDownLatch
            import org.gradle.process.internal.health.memory.*

            task waitForEvent {
                doLast {
                    final CountDownLatch notification = new CountDownLatch(1)
                    
                    MemoryResourceManager manager = project.services.get(MemoryResourceManager.class)
                    manager.addListener(new MemoryStatusListener() {
                        void onMemoryStatusNotification(MemoryStatus memoryStatus) {
                            println "MemoryStatus notification: $memoryStatus"
                            notification.countDown()
                        }
                    })
                    logger.warn "Waiting for memory status event..."
                    notification.await()
                }
            }
        '''.stripIndent()

        when:
        executer.withTasks("waitForEvent").withArgument("--debug").run()

        then:
        daemons.daemons.size() == 1
        daemons.daemon.log.contains 'MemoryStatus notification'
    }
}
