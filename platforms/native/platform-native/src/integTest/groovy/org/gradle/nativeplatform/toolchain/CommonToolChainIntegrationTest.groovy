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

package org.gradle.nativeplatform.toolchain

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import spock.lang.Issue

class CommonToolChainIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Issue("https://github.com/gradle/gradle-native/issues/139")
    def "can rely on working directory to be project directory"() {
        def app = new CHelloWorldApp()

        given:
        buildFile << """
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            binaries.all {
                lib library: 'hello', linkage: 'api'

                def librarySearchPath = 'build/libs/hello/static'
                def libraryName = 'hello'
                if (toolChain in VisualCpp) {
                    linker.args "/LIBPATH:\${librarySearchPath}", "\${libraryName}.lib"
                } else {
                    linker.args "-L\${librarySearchPath}", "-l\${libraryName}"
                }
            }
        }
        hello(NativeLibrarySpec)
    }
}
"""
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/hello"))
        succeeds 'helloStaticLibrary'

        when:
        succeeds 'mainExe'

        then:
        executable('build/exe/main/main').exec().out == app.englishOutput
    }
}
