/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class CppCommandLineLengthLimitIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    /*
     * The windows command line length limit is 32767 characters according to https://devblogs.microsoft.com/oldnewthing/20031210-00/?p=41553.
     * This test builds a cpp library with 330 generated source files where each file path is more than 100 characters long.
     * This could cause the command line for the compiler, linker or archiver to exceed that limit.
     * Fewer source files may already cause this problem depending on the path length of the files.
     */
    @Requires(TestPrecondition.WINDOWS)
    @Issue("gradle/gradle-native#1028")
    def "can build libraries where the source file paths collectively exceed the windows command line length limit"() {
        when:
        buildFile << '''
            plugins {
                id "cpp-library"
            }

            tasks.register("generateSource") {
                ext.sourceDir = layout.buildDirectory.dir("src-gen")
                outputs.dir sourceDir
                doLast {
                    def srcGen = sourceDir.get()
                    mkdir srcGen
                    (1..330).each {
                        srcGen.file("this_file_name_is_more_than_one_hundred_characters_long_to_exceed_the_windows_command_line_length_limit_${it}.cpp").asFile
                            .text = "int foo${it}() { return ${it}; }"
                    }
                }
            }

            library {
                source.from generateSource
                linkage = [Linkage.STATIC, Linkage.SHARED]
            }
        '''
        then:
        succeeds "assembleDebugStatic", "assembleDebugShared"
    }
}