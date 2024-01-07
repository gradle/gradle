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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.process.internal.worker.WorkerDiagnosticsLogging

class MemoryManagerDiagnosticsIntegrationTest extends AbstractIntegrationSpec implements MemoryStatusFixture {
    def "can get memory management diagnostics"() {
        given:
        buildFile << """
            import org.gradle.workers.*

            apply plugin: 'groovy'

            dependencies {
                implementation localGroovy()
            }

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
            }

            tasks.withType(GroovyCompile).configureEach {
                options.fork = true
            }

            ${waitForMemoryEventsTask()}

            task killWorker {
                dependsOn tasks.withType(JavaCompile)
                dependsOn tasks.withType(GroovyCompile)
                dependsOn waitForMemoryEvents
                doLast {
                    def queue = services.get(WorkerExecutor).processIsolation()

                    queue.submit(AbnormalExitWorker) { }
                }
            }
            abstract class AbnormalExitWorker implements WorkAction<WorkParameters.None> {
                @Override
                void execute() {
                    System.exit(137)
                }
            }
        """
        file('src/main/java/Foo.java') << """
            public class Foo { }
        """
        file('src/main/groovy/Bar.groovy') << """
            class Bar { }
        """
        executer.withWorkerDaemonsExpirationDisabled()

        expect:
        args("-D${WorkerDiagnosticsLogging.WORKER_DIAGNOSTICS_PROPERTY}=true")
        fails('killWorker')
    }
}
