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
package org.gradle.nativeplatform.sourceset

import org.apache.commons.io.FileUtils
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.app.CHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.CppHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.WindowsResourceHelloWorldApp

import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.SUPPORTS_32
import static org.gradle.nativeplatform.fixtures.ToolChainRequirement.VISUALCPP
// TODO: Test incremental
class GeneratedSourcesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
class GenerateSources extends DefaultTask {
    @InputDirectory File inputDir
    @OutputDirectory File sourceDir
    @OutputDirectory @Optional File headerDir

    @TaskAction
    void processInputFiles() {
        project.copy {
            from inputDir
            into sourceDir.parentFile
            filter { String line ->
                line.replaceAll('REMOVE_ME', '')
            }
        }
    }
}
task generateCSources(type: GenerateSources) {
    inputDir = project.file("src/input")
    headerDir = project.file("build/src/generated/headers")
    sourceDir = project.file("build/src/generated/c")
}
"""
    }

    private void degenerateInputSources() {
        FileUtils.listFiles(file("src/input"), null, true).each { File file ->
            file.text = "REMOVE_ME\n" + file.text
        }
    }

    @ToBeFixedForConfigurationCache
    def "generator task produces c sources and headers"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c.generatedBy tasks.generateCSources
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "generator task produces sources for dependent source set with headers only"() {
        given:
        // Write sources to src/main, headers to src/input
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.sourceFiles*.writeToDir(file("src/main"))
        app.library.headerFiles*.writeToDir(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
    apply plugin: 'c'

model {
    components { comp ->
        headersOnly(NativeLibrarySpec) {
            sources {
                c.generatedBy tasks.generateCSources
            }
        }
        main(NativeExecutableSpec) {
            sources {
                c.lib \$.components.headersOnly.sources.c
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "generator task produces sources for dependent source set"() {
        given:
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                generatedC(CSourceSet) {
                    generatedBy tasks.generateCSources
                }
                c.lib sources.generatedC
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "can have library composed of generated sources"() {
        given:
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c.lib library: 'hello', linkage: 'static'
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                c {
                    generatedBy tasks.generateCSources
                }
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "can depend on header-only library composed of generated sources"() {
        given:
        // Write sources to src/main, headers to src/hello
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.sourceFiles*.writeToDir(file("src/main"))
        app.library.headerFiles*.writeToDir(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c {
                lib library: 'hello', linkage: 'api'
                }
            }
        }
        hello(NativeLibrarySpec) {
            sources {
                c {
                    generatedBy tasks.generateCSources
                }
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "generator task produces cpp sources"() {
        given:
        def app = new CppHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'cpp'

task generateCppSources(type: GenerateSources) {
    inputDir = project.file("src/input")
    headerDir = project.file("build/src/generated/headers")
    sourceDir = project.file("build/src/generated/cpp")
}

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                cpp.generatedBy tasks.generateCppSources
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @RequiresInstalledToolChain(SUPPORTS_32)
    @ToBeFixedForConfigurationCache
    def "generator task produces assembler sources"() {
        given:
        def app = new MixedLanguageHelloWorldApp(AbstractInstalledToolChainIntegrationSpec.toolChain)
        def asmSources = app.sourceFiles.findAll({it.path == 'asm'})
        def mainSources = app.headerFiles + app.sourceFiles.findAll({it.path != 'asm'})
        mainSources.removeAll {it.path == 'asm'}
        mainSources*.writeToDir(file("src/main"))
        asmSources*.writeToDir(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
        buildFile << """
task generateAsmSources(type: GenerateSources) {
    inputDir = project.file("src/input")
    sourceDir = project.file("build/src/generated/asm")
}

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                asm.generatedBy tasks.generateAsmSources
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @RequiresInstalledToolChain(VISUALCPP)
    @ToBeFixedForConfigurationCache
    def "generator task produces windows resources"() {
        given:
        def app = new WindowsResourceHelloWorldApp()
        def rcSources = app.sourceFiles.findAll {it.path == 'rc'}
        def mainSources = app.headerFiles + app.sourceFiles - rcSources
        mainSources*.writeToDir(file("src/main"))
        rcSources*.writeToDir(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
        buildFile << """
task generateRcSources(type: GenerateSources) {
    inputDir = project.file("src/input")
    sourceDir = project.file("build/src/generated/rc")
}

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                rc.generatedBy tasks.generateRcSources
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    def "produces reasonable error message when generator task does not have sourceDir property"() {
        when:
        buildFile << """
apply plugin: 'c'

task generateSources {
}

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c.generatedBy tasks.generateSources
            }
        }
    }
}
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasCause "Exception thrown while executing model rule: NativeComponentModelPlugin.Rules#configureGeneratedSourceSets"
        failure.assertHasCause "Could not get unknown property 'sourceDir' for task ':generateSources' of type org.gradle.api.DefaultTask."
    }

    @ToBeFixedForConfigurationCache
    def "can explicitly configure source and header directories from generator task"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c {
                    builtBy tasks.generateCSources
                    source {
                        srcDirs tasks.generateCSources.sourceDir
                    }
                    exportedHeaders {
                        srcDirs tasks.generateCSources.headerDir
                    }
                }
            }
        }
    }
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "can configure generator task properties after wiring"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
apply plugin: 'c'

task lateConfiguredGenerator(type: GenerateSources)

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c.generatedBy tasks.lateConfiguredGenerator
            }
        }
    }
}

lateConfiguredGenerator {
    inputDir = project.file("src/input")
    headerDir = project.file("build/src/generated/headers")
    sourceDir = project.file("build/src/generated/c")
}
"""

        then:
        expectTaskGetProjectDeprecations()
        executableBuilt(app)
    }

    @ToBeFixedForConfigurationCache
    def "creates visual studio project including generated sources"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        and:
        buildFile << """
apply plugin: 'visual-studio'
apply plugin: 'c'

model {
    components {
        main(NativeExecutableSpec) {
            sources {
                c.generatedBy tasks.generateCSources
            }
        }
    }
}
"""

        when:
        expectTaskGetProjectDeprecations()
        succeeds "visualStudio"

        then:
        final mainSolution = new SolutionFile(file("test.sln"))
        mainSolution.assertHasProjects("mainExe")

        and:
        final projectFile = new ProjectFile(file("mainExe.vcxproj"))
        projectFile.sourceFiles as Set == [
                "build.gradle",
                "build/src/generated/c/hello.c",
                "build/src/generated/c/main.c",
                "build/src/generated/c/sum.c"
        ] as Set
        projectFile.headerFiles.sort() == [ "build/src/generated/headers/common.h", "build/src/generated/headers/hello.h" ]
        projectFile.projectConfigurations.keySet() == ['debug'] as Set
        with (projectFile.projectConfigurations['debug']) {
            // TODO - should not include the default location
            includePath == "src/main/headers;build/src/generated/headers"
        }
    }

    def executableBuilt(def app) {
        succeeds "mainExecutable"
        assert executable("build/exe/main/main").exec().out == app.englishOutput
        true
    }
}
