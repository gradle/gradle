/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.objectivec

import org.gradle.language.AbstractNativeLanguageIncrementalCompileIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.ObjectiveCHelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.GCC_COMPATIBLE

@RequiresInstalledToolChain(GCC_COMPATIBLE)
@Requires(UnitTestPreconditions.NotWindows)
class ObjectiveCLanguageIncrementalCompileIntegrationTest extends AbstractNativeLanguageIncrementalCompileIntegrationTest {
    @Override
    IncrementalHelloWorldApp getHelloWorldApp() {
        return new ObjectiveCHelloWorldApp()
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
        outputs.noneRecompiled()

        where:
        testCase                       | headerDirs
        "extra header dir after"       | '"src/main/headers", "src/additional-headers"'
        "extra header dir before"      | '"src/additional-headers", "src/main/headers"'
        "replacement header dir after" | '"src/main/headers", "src/replacement-headers"'
    }

    def "recompiles only source file that imported changed header file"() {
        given:
        sourceFile << """
            #import "${otherHeaderFile.name}"
"""
        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }

    def "source is always recompiled if it imported header via complex macro"() {
        given:
        def notIncluded = file("src/main/headers/notIncluded.h")
        notIncluded.text = """#pragma message("should not be used")"""
        sourceFile << """
            #define _MY_HEADER(X) #X
            #define MY_HEADER _MY_HEADER(${otherHeaderFile.name})
            #import MY_HEADER
"""

        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile

        when: "Header that is NOT included is changed"
        notIncluded << """
            // Dummy header file
"""
        and:
        run "mainExecutable"

        then: "Source is still recompiled"
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }

    def "recompiles source file when transitively imported header file is changed"() {
        given:
        def transitiveHeaderFile = file("src/main/headers/transitive.h") << """
           // Dummy header file
"""
        otherHeaderFile << """
            #import "${transitiveHeaderFile.name}"
"""
        sourceFile << """
            #import "${otherHeaderFile.name}"
"""

        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        transitiveHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }
}
