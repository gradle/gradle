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

package org.gradle.jvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaCompilationSoakTest extends AbstractIntegrationSpec {
    def setup() {
        // projectCount * memoryHog ~= 25% default JVM heap size
        def projectCount = 5
        executer.requireIsolatedDaemons()

        def subprojects = []
        projectCount.times {
            subprojects << "sub" + it
        }
        multiProjectBuild("root", subprojects) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'

                    // ~25MB
                    ext.memoryHog = new byte[1024*1024*25]

                    tasks.withType(JavaCompile).configureEach {
                        options.fork = true
                        options.forkOptions.jvmArgs << "-Dcounter=" + project.property("counter")
                    }
                }
            """
        }
    }

    def "can recompile many times in a row with a changing set of compiler daemons"() {
        expect:
        10.times {
            println("Run $it")
            succeeds("clean", "assemble", "-Pcounter="+it)
        }
    }

    def "can recompile many times in a row with a reused compiler daemon"() {
        expect:
        10.times {
            println("Run $it")
            succeeds("clean", "assemble", "-Pcounter=0")
        }
    }
}
