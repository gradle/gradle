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
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp

class PreCompiledHeaderSourcesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "can set a precompiled header on a source set" () {
        given:
        def app = new CHelloWorldApp()
        settingsFile << "rootProject.name = 'test'"
        app.mainSource.writeToDir(file("src/main"))
        app.libraryHeader.writeToDir(file("src/hello")).append('\n#pragma message("<==== compiling header ====>")')
        app.librarySources.each {
            def noHeaderInclude = new SourceFile(it.path, it.name, modifyContent(it.content))
            noHeaderInclude.writeToDir(file("src/hello"))
        }
        assert file("src/hello/headers/hello.h").exists()

        when:
        buildFile << """
            apply plugin: 'c'

            model {
                components {
                    main(NativeExecutableSpec) {
                        binaries.all {
                            lib library: 'hello'
                        }
                    }
                    hello(NativeLibrarySpec) {
                        sources {
                            c.preCompiledHeader file("src/hello/headers/hello.h")
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "compileHelloSharedLibraryCPreCompiledHeader"
        output.contains("<==== compiling header ====>")
        def outputDirectories = file("build/objs/helloSharedLibrary/CPreCompiledHeader").listFiles().findAll { it.isDirectory() }
        assert outputDirectories.size() == 1
        assert outputDirectories[0].assertContainsDescendants("hello.${getSuffix()}")

        and:
        args("--info")
        succeeds "compileHelloSharedLibraryHelloC"
        skipped ":compileHelloSharedLibraryCPreCompiledHeader"
        ! output.contains("<==== compiling header ====>")
    }

    String getSuffix() {
        return toolChain.displayName == "visual c++" ? "pch" : "h.gch"
    }

    String modifyContent(content) {
        return toolChain.displayName == "visual c++" ? content : content - "#include \"hello.h\""
    }
}
