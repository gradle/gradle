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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout

@IntegrationTestTimeout(120)
class WorkerDaemonExpirationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        given:
        executer.requireIsolatedDaemons()
        executer.requireDaemon()

        and:
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """.stripIndent()
        buildFile << """
            import java.util.concurrent.CountDownLatch
            import org.gradle.process.internal.health.memory.OsMemoryStatus
            import org.gradle.process.internal.health.memory.OsMemoryStatusListener
            import org.gradle.process.internal.health.memory.MemoryManager

            task expireWorkers {
                doFirst {
                    def freeMemory
                    CountDownLatch latch = new CountDownLatch(1)
                    def memoryManager = services.get(MemoryManager.class)
                    memoryManager.addListener(new OsMemoryStatusListener() {
                        public void onOsMemoryStatus(OsMemoryStatus osMemoryStatus) {
                            freeMemory = osMemoryStatus.getPhysicalMemory().getFree()
                            latch.countDown()
                        }
                    })
                    latch.await()

                    // Force worker daemon expiration to occur
                    memoryManager.requestFreeMemory(freeMemory * 2)
                }
            }

            subprojects { p ->
                apply plugin: 'java'
                tasks.withType(JavaCompile) { task ->
                    task.options.fork = true
                    rootProject.tasks.expireWorkers.dependsOn task
                }
            }
        """.stripIndent()
        ['a', 'b'].each { file("$it/src/main/java/p/Type.java") << 'package p; class Type {}' }
    }

    def "expire worker daemons to free system memory"() {
        when:
        withDebugLogging()
        succeeds 'expireWorkers'

        then:
        outputContains 'Worker Daemon(s) expired to free some system memory'
    }

    def "worker daemons expiration can be disabled using a system property"() {
        when:
        withDebugLogging()
            .withWorkerDaemonsExpirationDisabled()
        succeeds('expireWorkers')

        then:
        outputDoesNotContain('Worker Daemon(s) expired to free some system memory')
        outputContains 'Worker Daemon(s) had expiration disabled and were skipped'
    }
}
