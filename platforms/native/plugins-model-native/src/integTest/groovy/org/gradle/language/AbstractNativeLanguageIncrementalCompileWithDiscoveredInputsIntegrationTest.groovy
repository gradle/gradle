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

package org.gradle.language

abstract class AbstractNativeLanguageIncrementalCompileWithDiscoveredInputsIntegrationTest extends AbstractNativeLanguageIncrementalCompileIntegrationTest {

    String getDependTask() {
        ":dependMainExecutableMain${sourceType}"
    }

    def "does not recompile when include path has #testCase"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        file("src/additional-headers/other.h") << """
    // extra header file that is not included in source
"""
        file("src/replacement-headers/${sharedHeaderFile.name}") << """
    // replacement header file that is included in source
"""

        when:
        buildFile << """
    model {
        components {
            main {
                sources {
                    ${app.sourceType} {
                        exportedHeaders {
                            srcDirs ${headerDirs}
                        }
                    }
                }
            }
        }
    }
"""
        and:
        run "mainExecutable"

        then:
        skipped compileTask

        and:
        outputs.noneRecompiled()

        where:
        testCase                       | headerDirs
        "extra header dir after"       | '"src/main/headers", "src/additional-headers"'
        "extra header dir before"      | '"src/additional-headers", "src/main/headers"'
        "replacement header dir after" | '"src/main/headers", "src/replacement-headers"'
    }

}
