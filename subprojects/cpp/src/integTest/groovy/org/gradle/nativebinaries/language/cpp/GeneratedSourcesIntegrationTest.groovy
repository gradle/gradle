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
package org.gradle.nativebinaries.language.cpp
import org.apache.commons.io.FileUtils
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.WindowsResourceHelloWorldApp

import static org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement.VisualCpp

// TODO:DAZ Test incremental
class GeneratedSourcesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def generatorTask

    def setup() {
        generatorTask = """
    task generateSources(type: Copy) {
        from "src/input"
        into "\${buildDir}/src/generated"
        filter { String line ->
            if (line.startsWith("REMOVE ME")) {
                ""
            } else {
                line
            }
        }
    }
"""
    }

    private void degenerateInputSources() {
        FileUtils.listFiles(file("src/input"), null, true).each { File file ->
            file.text = "REMOVE ME\n" + file.text
        }
    }

    def "test primary source set generated"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        buildFile << """
    apply plugin: 'c'

    $generatorTask

    executables {
        main {}
    }
    sources {
        main {
            c {
                builtBy tasks.generateSources
                source {
                    srcDir "\${buildDir}/src/generated/c"
                }
                exportedHeaders {
                    srcDirs "\${buildDir}/src/generated/headers"
                }
            }
        }
    }
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    def "test dependent headers source set generated"() {
        given:
        // Write sources to src/main, headers to src/input
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.sourceFiles*.writeToDir(file("src/main"))
        app.library.headerFiles*.writeToDir(file("src/input"))
        degenerateInputSources()

        and:
        buildFile << """
    apply plugin: 'c'

    $generatorTask

    executables {
        main {}
    }
    sources {
        generated {
            cHeaders(CSourceSet) {
                builtBy tasks.generateSources
                exportedHeaders {
                    srcDirs "\${buildDir}/src/generated/headers"
                }
            }
        }
    }
    sources.main.c.lib sources.generated.cHeaders
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    def "test dependent source set generated"() {
        given:
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/input"))
        degenerateInputSources()

        and:
        buildFile << """
    apply plugin: 'c'

    $generatorTask

    executables {
        main {}
    }
    sources {
        generated {
            c {
                builtBy tasks.generateSources
                source {
                    srcDir "\${buildDir}/src/generated/c"
                }
                exportedHeaders {
                    srcDirs "\${buildDir}/src/generated/headers"
                }
            }
        }
    }
    sources.main.c.lib sources.generated.c
    executables.main.source sources.generated.c
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    def "test generated cpp"() {
        given:
        def app = new CppHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        buildFile << """
    apply plugin: 'cpp'

    $generatorTask

    executables {
        main {}
    }
    sources {
        main {
            cpp {
                builtBy tasks.generateSources
                source {
                    srcDir "\${buildDir}/src/generated/cpp"
                }
                exportedHeaders {
                    srcDirs "\${buildDir}/src/generated/headers"
                }
            }
        }
    }
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    def "test generated asm"() {
        given:
        def app = new MixedLanguageHelloWorldApp(toolChain)
        def asmSources = app.sourceFiles.findAll {it.path == 'asm'}
        def mainSources = app.headerFiles + app.sourceFiles - asmSources
        mainSources*.writeToDir(file("src/main"))
        asmSources*.writeToDir(file("src/input"))
        degenerateInputSources()

        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
        buildFile << """
    $generatorTask

    executables {
        main {}
    }
    sources {
        main {
            asm {
                builtBy tasks.generateSources
                source {
                    srcDir "\${buildDir}/src/generated/asm"
                }
            }
        }
    }
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }

    @RequiresInstalledToolChain(VisualCpp)
    def "test generated windows resources"() {
        given:
        def app = new WindowsResourceHelloWorldApp()
        def rcSources = app.sourceFiles.findAll {it.path == 'rc'}
        def mainSources = app.headerFiles + app.sourceFiles - rcSources
        mainSources*.writeToDir(file("src/main"))
        rcSources*.writeToDir(file("src/input"))
        degenerateInputSources()

        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
        buildFile << """
    $generatorTask

    executables {
        main {}
    }
    sources {
        main {
            rc {
                builtBy tasks.generateSources
                source {
                    srcDir "\${buildDir}/src/generated/rc"
                }
            }
        }
    }
"""

        when:
        succeeds "mainExecutable"

        then:
        executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
    }
}
