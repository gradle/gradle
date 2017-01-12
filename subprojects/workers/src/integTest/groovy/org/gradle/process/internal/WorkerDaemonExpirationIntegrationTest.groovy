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

package org.gradle.process.internal

import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.internal.health.memory.DefaultMemoryManager
import org.gradle.process.internal.health.memory.MemoryInfo

class WorkerDaemonExpirationIntegrationTest extends AbstractIntegrationSpec {

    def freeMemory = new MemoryInfo(new DefaultExecActionFactory(new IdentityFileResolver())).getFreePhysicalMemory();

    def "expire worker daemons to free system memory"() {
        given:
        executer.requireIsolatedDaemons()
        executer.requireDaemon()

        and:
        def workerHeapSizeMB = (freeMemory / 1024 / 1024) as long
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """.stripIndent()
        buildFile << """
            def heapSize = [
                a: ${workerHeapSizeMB},
                b: ${workerHeapSizeMB + 1}
            ]
            subprojects { p ->
                apply plugin: 'java'
                tasks.withType(JavaCompile) { task ->
                    task.doFirst {
                        // Wait for memory status events
                        Thread.sleep(${DefaultMemoryManager.STATUS_INTERVAL_SECONDS * 1000})
                    }
                    task.options.fork = true
                    task.options.forkOptions.memoryInitialSize = "\${heapSize[p.name]}m"
                    task.options.forkOptions.memoryMaximumSize = "\${heapSize[p.name]}m"
                }
            }
        """.stripIndent()
        ['a', 'b'].each { file("$it/src/main/java/p/Type.java") << 'package p; class Type {}' }

        when:
        args '--debug'
        succeeds 'compileJava'

        then:
        result.output.contains 'Worker Daemon(s) expired to free some system memory'
    }
}
