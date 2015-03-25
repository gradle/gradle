/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.sourceset

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CPCHHelloWorldApp
import org.spockframework.util.TextUtil

class PreCompiledHeaderSourcesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can set a precompiled header on a source set for a relative source header" () {
        given:
        def app = new CPCHHelloWorldApp()
        settingsFile << "rootProject.name = 'test'"
        app.getLibraryHeader(path).writeToDir(file("src/hello"))
        app.getLibrarySources(path).each {
            it.writeToDir(file("src/hello"))
        }
        assert file("src/hello/headers/${path}hello.h").exists()

        when:
        buildFile << """
            apply plugin: 'c'

            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            c.preCompiledHeader "${path}hello.h"
                        }
                        binaries.all {
                            if (toolChain.name == "visualCpp") {
                                cCompiler.args "/showIncludes"
                            } else {
                                cCompiler.args "-H"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "compileHelloSharedLibraryCPreCompiledHeader"
        executed ":generateHelloSharedLibraryCPrefixHeaderFile"
        output.contains("<==== compiling hello.h ====>")
        def outputDirectories = file("build/objs/helloSharedLibrary/CPreCompiledHeader").listFiles().findAll { it.isDirectory() }
        assert outputDirectories.size() == 1
        assert outputDirectories[0].assertContainsDescendants("prefix-headers.${getSuffix()}")

        and:
        args("--info")
        succeeds "compileHelloSharedLibraryHelloC"
        skipped ":generateHelloSharedLibraryCPrefixHeaderFile", ":compileHelloSharedLibraryCPreCompiledHeader"
        ! output.contains("<==== compiling hello.h ====>")

        where:
        path << [ "", "subdir/to/header/" ]
    }

    def "can set a precompiled header on a source set for a source header in include path" () {
        given:
        def app = new CPCHHelloWorldApp()
        settingsFile << "rootProject.name = 'test'"
        app.getLibraryHeader(path).writeToDir(file("src/include"))
        app.getLibrarySources(path).each {
            it.writeToDir(file("src/hello"))
        }
        assert file("src/include/headers/${path}hello.h").exists()

        when:
        def headerDir = file("src/include/headers")
        def safeHeaderDirPath = TextUtil.escape(headerDir.absolutePath)
        buildFile << """
            apply plugin: 'c'

            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            c.preCompiledHeader "${path}hello.h"
                        }
                        binaries.all {
                            if (toolChain.name == "visualCpp") {
                                cCompiler.args "/I${safeHeaderDirPath}", "/showIncludes"
                            } else {
                                cCompiler.args "-I${safeHeaderDirPath}", "-H"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "compileHelloSharedLibraryCPreCompiledHeader"
        executed ":compileHelloSharedLibraryCPreCompiledHeader"
        output.contains("<==== compiling hello.h ====>")
        def outputDirectories = file("build/objs/helloSharedLibrary/CPreCompiledHeader").listFiles().findAll { it.isDirectory() }
        assert outputDirectories.size() == 1
        assert outputDirectories[0].assertContainsDescendants("prefix-headers.${getSuffix()}")

        and:
        args("--info")
        succeeds "compileHelloSharedLibraryHelloC"
        skipped ":generateHelloSharedLibraryCPrefixHeaderFile", ":compileHelloSharedLibraryCPreCompiledHeader"
        ! output.contains("<==== compiling hello.h ====>")

        where:
        path << [ "", "subdir/" ]
    }

    def "can set a precompiled header on a source set for a system header" () {
        given:
        def app = new CPCHHelloWorldApp()
        settingsFile << "rootProject.name = 'test'"
        app.libraryHeader.writeToDir(file("src/hello"))
        app.getSystemHeader(path).writeToDir(file("src/systemHeader"))
        app.librarySources.each {
            SourceFile library = new SourceFile(it.path, it.name, "#include <${path}systemHeader.h>\n" + it.content)
            library.writeToDir(file("src/hello"))
        }
        assert file("src/systemHeader/headers/${path}systemHeader.h").exists()

        when:
        def systemHeaderDir = file("src/systemHeader/headers")
        def safeHeaderDirPath = TextUtil.escape(systemHeaderDir.absolutePath)
        buildFile << """
            apply plugin: 'c'

            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            c.preCompiledHeader "<${path}systemHeader.h>"
                        }
                        binaries.all {
                            println toolChain
                            if (toolChain.name == "visualCpp") {
                                cCompiler.args "/I${safeHeaderDirPath}", "/showIncludes"
                            } else {
                                cCompiler.args "-I${safeHeaderDirPath}", "-H"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "compileHelloSharedLibraryCPreCompiledHeader"
        executed ":compileHelloSharedLibraryCPreCompiledHeader"
        output.contains("<==== compiling systemHeader.h ====>")
        def outputDirectories = file("build/objs/helloSharedLibrary/CPreCompiledHeader").listFiles().findAll { it.isDirectory() }
        assert outputDirectories.size() == 1
        assert outputDirectories[0].assertContainsDescendants("prefix-headers.${getSuffix()}")

        and:
        args("--info")
        succeeds "compileHelloSharedLibraryHelloC"
        skipped ":compileHelloSharedLibraryCPreCompiledHeader", ":compileHelloSharedLibraryCPreCompiledHeader"
        ! output.contains("<==== compiling systemHeader.h ====>")

        where:
        path << [ "", "subdir/" ]
    }

    def "can set multiple precompiled headers on a source set" () {
        given:
        def app = new CPCHHelloWorldApp()
        settingsFile << "rootProject.name = 'test'"
        app.getLibraryHeader().writeToDir(file("src/hello"))
        app.getLibrarySources().each {
            it.writeToDir(file("src/hello"))
        }
        assert file("src/hello/headers/hello.h").exists()

        when:
        buildFile << """
            apply plugin: 'c'

            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            c.preCompiledHeader "hello.h"
                            c.preCompiledHeader "<stdio.h>"
                        }
                        binaries.all {
                            if (toolChain.name == "visualCpp") {
                                cCompiler.args "/showIncludes"
                            } else {
                                cCompiler.args "-H"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "compileHelloSharedLibraryHelloC"
        executed ":generateHelloSharedLibraryCPrefixHeaderFile", ":compileHelloSharedLibraryCPreCompiledHeader"
        // once for PCH compile, once for compile of sum.c, but not for hello.c
        output.count(getUniquePragmaOutput("<==== compiling hello.h ====>")) == 2
    }

    String getSuffix() {
        return toolChain.displayName == "visual c++" ? "pch" : "h.gch"
    }

    String getUniquePragmaOutput(String message) {
        return toolChain.displayName == "visual c++" ? message : "warning: ${message}"
    }
}
