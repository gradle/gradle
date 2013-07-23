/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.language.cpp

import org.gradle.nativecode.language.cpp.fixtures.app.HelloWorldApp
import org.gradle.nativecode.language.cpp.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.nativecode.language.cpp.fixtures.app.SourceFile

class MixedLanguageIntegrationTest extends AbstractLanguageIntegrationTest {
    HelloWorldApp helloWorldApp = new MixedLanguageHelloWorldApp()

    def "can have all source files co-located in a common directory"() {
        given:
        buildFile << """
            apply plugin: "cpp"
            sources {
                main {
                    cpp {
                        source {
                            srcDirs "src/main/flat"
                            include "**/*.cpp"
                        }
                    }
                    c {
                        source {
                            srcDirs "src/main/flat"
                            include "**/*.c"
                        }
                        exportedHeaders {
                            srcDirs "src/main/flat"
                        }
                    }
                    asm {
                        source {
                            srcDirs "src/main/flat"
                            include "**/*.s"
                        }
                    }
                }
            }
            executables {
                main {
                    source sources.main
                }
            }
            binaries.all {
                $helloWorldApp.customArgs
            }
        """

        and:
        helloWorldApp.sourceFiles.each { SourceFile sourceFile ->
            file("src/main/flat/${sourceFile.name}") << sourceFile.content
        }

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/binaries/mainExecutable/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

}

