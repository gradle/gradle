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

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp

class PreCompiledHeaderSourcesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "setting a precompiled header generates a source set and compile task" () {
        given:
        def app = new CHelloWorldApp()
        settingsFile << "rootProject.name = 'test'"
        def sourceFile = app.mainSource.writeToDir(file("src/main"))
        def headerFile = app.libraryHeader.writeToDir(file("src/hello"))
        app.librarySources.each {
            it.writeToDir(file("src/hello"))
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
        succeeds "mainExecutable"
        executedAndNotSkipped ":compileHelloSharedLibraryCPreCompiledHeader"
        def outputDirectories = file("build/objs/helloSharedLibrary/CPreCompiledHeader").listFiles().findAll { it.isDirectory() }
        assert outputDirectories.size() == 1
        assert outputDirectories[0].assertContainsDescendants("hello.${getSuffix()}")
    }

    String getSuffix() {
        return OperatingSystem.current().isWindows() ? "pch" : "h.pch"
    }
}
