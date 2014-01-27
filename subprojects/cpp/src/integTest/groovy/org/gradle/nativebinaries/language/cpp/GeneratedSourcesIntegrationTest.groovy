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
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.RequiresInstalledToolChain
import org.gradle.nativebinaries.language.cpp.fixtures.app.CHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.CppHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.MixedLanguageHelloWorldApp
import org.gradle.nativebinaries.language.cpp.fixtures.app.WindowsResourceHelloWorldApp

import static org.gradle.nativebinaries.language.cpp.fixtures.ToolChainRequirement.VisualCpp
// TODO:DAZ Test incremental
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
        inputDir project.file("src/input")
        headerDir project.file("build/src/generated/headers")
        sourceDir project.file("build/src/generated/c")
    }
"""
    }

    private void degenerateInputSources() {
        FileUtils.listFiles(file("src/input"), null, true).each { File file ->
            file.text = "REMOVE_ME\n" + file.text
        }
    }

    def "generator task produces c sources and headers"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
    apply plugin: 'c'

    executables {
        main {}
    }
    sources.main.c.generatedBy tasks.generateCSources
"""

        then:
        executableBuilt(app)
    }

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

    executables {
        main {}
    }
    sources {
        generated {
            cHeaders(CSourceSet) {
                generatedBy tasks.generateCSources
            }
        }
    }
    sources.main.c.lib sources.generated.cHeaders
"""

        then:
        executableBuilt(app)
    }

    def "generator task produces sources for dependent source set"() {
        given:
        def app = new CHelloWorldApp()
        app.executable.writeSources(file("src/main"))
        app.library.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
    apply plugin: 'c'

    executables {
        main {}
    }
    sources {
        generated {
            c {
                generatedBy tasks.generateCSources
            }
        }
    }
    sources.main.c.lib sources.generated.c
    executables.main.source sources.generated.c
"""

        then:
        executableBuilt(app)
    }

    def "generator task produces cpp sources"() {
        given:
        def app = new CppHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
    apply plugin: 'cpp'

    task generateCppSources(type: GenerateSources) {
        inputDir project.file("src/input")
        headerDir project.file("build/src/generated/headers")
        sourceDir project.file("build/src/generated/cpp")
    }

    executables {
        main {}
    }
    sources.main.cpp.generatedBy tasks.generateCppSources
"""

        then:
        executableBuilt(app)
    }

    def "generator task produces assembler sources"() {
        given:
        def app = new MixedLanguageHelloWorldApp(toolChain)
        def asmSources = app.sourceFiles.findAll {it.path == 'asm'}
        def mainSources = app.headerFiles + app.sourceFiles - asmSources
        mainSources*.writeToDir(file("src/main"))
        asmSources*.writeToDir(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
        buildFile << """
    task generateAsmSources(type: GenerateSources) {
        inputDir project.file("src/input")
        sourceDir project.file("build/src/generated/asm")
    }

    executables {
        main {}
    }
    sources.main.asm.generatedBy tasks.generateAsmSources
"""

        then:
        executableBuilt(app)
    }

    @RequiresInstalledToolChain(VisualCpp)
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
        inputDir project.file("src/input")
        sourceDir project.file("build/src/generated/rc")
    }

    executables {
        main {}
    }
    sources.main.rc.generatedBy tasks.generateRcSources
"""

        then:
        executableBuilt(app)
    }

    def "produces reasonable error message when generator task does not have sourceDir property"() {
        when:
        buildFile << """
    apply plugin: 'c'

    task generateSources {
    }

    executables {
        main {}
    }
    sources.main.c.generatedBy tasks.generateSources
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasDescription "A problem occurred configuring root project 'test'."
        failure.assertHasCause "Could not find property 'sourceDir' on task ':generateSources'."
    }

    def "can explicitly configure source and header directories from generator task"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
    apply plugin: 'c'

    executables {
        main {}
    }
    sources {
        main {
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
"""

        then:
        executableBuilt(app)
    }

    def "can configure generator task properties after wiring"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        when:
        buildFile << """
    apply plugin: 'c'

    task lateConfiguredGenerator(type: GenerateSources)

    executables {
        main {}
    }
    sources.main.c.generatedBy tasks.lateConfiguredGenerator

    lateConfiguredGenerator {
        inputDir project.file("src/input")
        headerDir project.file("build/src/generated/headers")
        sourceDir project.file("build/src/generated/c")
    }
"""

        then:
        executableBuilt(app)
    }

    def "creates visual studio project including generated sources"() {
        given:
        def app = new CHelloWorldApp()
        app.writeSources(file("src/input"))
        degenerateInputSources()

        and:
        buildFile << """
    apply plugin: 'visual-studio'
    apply plugin: 'c'

    executables {
        main {}
    }
    sources.main.c.generatedBy tasks.generateCSources
"""

        when:
        succeeds "mainVisualStudio"

        then:
        final mainSolution = new SolutionFile(file("mainExe.sln"))
        mainSolution.assertHasProjects("mainExe")

        and:
        final projectFile = new ProjectFile(file("mainExe.vcxproj"))
        projectFile.sourceFiles as Set == [
                "build.gradle",
                "build/src/generated/c/hello.c",
                "build/src/generated/c/main.c",
                "build/src/generated/c/sum.c"
        ] as Set
        projectFile.headerFiles == [ "build/src/generated/headers/hello.h" ]
        projectFile.projectConfigurations.keySet() == ['debug'] as Set
        with (projectFile.projectConfigurations['debug']) {
            includePath == "build/src/generated/headers"
        }
    }

    def executableBuilt(def app) {
        succeeds "mainExecutable"
        assert executable("build/binaries/mainExecutable/main").exec().out == app.englishOutput
        true
    }
}
