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

package org.gradle.language.c

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.language.AbstractNativeLanguageIntegrationTest
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.nativeplatform.fixtures.app.MixedLanguageHelloWorldApp

@RequiresInstalledToolChain(ToolChainRequirement.SUPPORTS_32_AND_64)
class MixedLanguageIntegrationTest extends AbstractNativeLanguageIntegrationTest {

    @Override
    HelloWorldApp getHelloWorldApp() {
        return new MixedLanguageHelloWorldApp(toolChain)
    }

    @ToBeFixedForConfigurationCache
    def "can have all source files co-located in a common directory"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
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
    }
}
        """

        and:
        helloWorldApp.files.each { SourceFile sourceFile ->
            sourceFile.writeToFile(file("src/main/flat/${sourceFile.name}"))
        }

        when:
        run "mainExecutable"

        then:
        def mainExecutable = executable("build/exe/main/main")
        mainExecutable.assertExists()
        mainExecutable.exec().out == helloWorldApp.englishOutput
    }

    @ToBeFixedForConfigurationCache
    def "build and execute program with non-conventional source layout"() {
        given:
        buildFile << """
model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp {
                    source {
                        srcDirs "source"
                        include "**/*.cpp"
                    }
                    exportedHeaders {
                        srcDirs "source/hello", "include"
                    }
                }
                c {
                    source {
                        srcDirs "source", "include"
                        include "**/*.c"
                    }
                    exportedHeaders {
                        srcDirs "source/hello", "include"
                    }
                }
            }
        }
    }
}
        """
        settingsFile << "rootProject.name = 'test'"

        and:
        file("source", "hello", "hello.cpp") << """
            #include <iostream>

            void hello () {
              std::cout << "${HelloWorldApp.HELLO_WORLD}";
            }
        """

        and:
        file("source", "hello", "french", "bonjour.c") << """
            #include <stdio.h>
            #include "otherProject/bonjour.h"

            void bonjour() {
                printf("${HelloWorldApp.HELLO_WORLD_FRENCH}");
            }
        """

        and:
        file("source", "hello", "hello.h") << """
            void hello();
        """

        and:
        file("source", "app", "main", "main.cpp") << """
            #include "hello.h"
            extern "C" {
                #include "otherProject/bonjour.h"
            }

            int main () {
              hello();
              bonjour();
              return 0;
            }
        """

        and:
        file("include", "otherProject", "bonjour.h") << """
            void bonjour();
        """

        and:
        file("include", "otherProject", "bonjour.cpp") << """
            THIS FILE WON'T BE COMPILED
        """
        file("src", "main", "cpp", "bad.cpp") << """
            THIS FILE WON'T BE COMPILED
        """
        file("src", "main", "headers", "hello.h") << """
            THIS FILE WON'T BE INCLUDED
        """

        when:
        run "mainExecutable"

        then:
        executable("build/exe/main/main").exec().out == HelloWorldApp.HELLO_WORLD + HelloWorldApp.HELLO_WORLD_FRENCH
    }
}

