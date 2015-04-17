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

package org.gradle.language

import org.apache.commons.lang.StringUtils
import org.gradle.integtests.fixtures.SourceFile
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.Matchers
import org.spockframework.util.TextUtil

abstract class AbstractNativePreCompiledHeaderIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    abstract IncrementalHelloWorldApp getApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
    }

    def "can set a precompiled header on a source set for a source header in the headers directory" () {
        given:
        standardSourceFiles(path)

        when:
        buildFile << preCompiledHeaderComponent(path)

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ])

        when:
        librarySourceModified(path)

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibPCHNotCompiled()

        where:
        path << [ "", "subdir/to/header/" ]
    }

    def "can set a precompiled header on a source set for a relative source header colocated with the source" () {
        given:
        new SourceFile(app.sourceType, "hello.h", app.libraryHeader.content).writeToDir(file("src/hello"))
        app.librarySources.each { it.writeToDir(file("src/hello")) }
        new SourceFile(app.sourceType, "common.h", app.commonHeader.content).writeToDir(file("src/hello"))

        when:
        buildFile << preCompiledHeaderComponent()
        buildFile << """
            model {
                components {
                    hello {
                        sources {
                            ${app.sourceType}.source.include "**/*.${app.sourceExtension}"
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ])

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibPCHNotCompiled()
    }

    def "can set a precompiled header on a source set for a source header in include path" () {
        given:
        app.libraryHeader.writeToDir(file("src/include"))
        getLibrarySources(path).each { it.writeToDir(file("src/hello")) }
        getCommonHeader(path).writeToDir(file("src/include"))

        when:
        def headerDir = file("src/include/headers")
        def safeHeaderDirPath = TextUtil.escape(headerDir.absolutePath)
        buildFile << preCompiledHeaderComponent(path)
        buildFile << """
            model {
                components {
                    hello {
                        binaries.all {
                            if (toolChain.name == "visualCpp") {
                                ${app.sourceType}Compiler.args "/I${safeHeaderDirPath}"
                            } else {
                                ${app.sourceType}Compiler.args "-I${safeHeaderDirPath}"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ])

        when:
        librarySourceModified(path)

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibPCHNotCompiled()

        where:
        path << [ "", "subdir/" ]
    }

    def "a precompiled header on a source set gets used for all variants of a binary" () {
        given:
        standardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "assemble"
        sharedLibAndPCHTasksExecuted()
        staticLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName, staticPCHHeaderDirName ])

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "assemble"
        sharedLibPCHNotCompiled()
        staticLibPCHNotCompiled()
    }

    def "can have source sets both with and without precompiled headers" () {
        given:
        standardSourceFiles()
        app.libraryHeader.writeToDir(file("src/hello2"))
        app.librarySources.find { it.name.startsWith("hello") }.writeToDir(file("src/hello2"))
        app.commonHeader.writeToDir(file("src/hello2"))

        when:
        buildFile << preCompiledHeaderComponent()
        buildFile << """
            model {
                components {
                    hello2(NativeLibrarySpec)
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ])

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibPCHNotCompiled()

        and:
        args("--info")
        succeeds "hello2SharedLibrary"
        executedAndNotSkipped ":${sharedLibraryCompileTaskName.replaceAll('Hello', 'Hello2')}"
        notExecuted ":${generatePrefixHeaderTaskName}", ":${sharedPCHCompileTaskName}"
        // once for hello2.c only
        output.count(getUniquePragmaOutput("<==== compiling hello.h ====>")) == 1
    }

    def "can have sources that do not use precompiled header" () {
        given:
        standardSourceFiles()
        libraryWithoutPCH.writeToDir(file("src/hello"))

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        // once for PCH, once for source file without PCH
        output.count(getUniquePragmaOutput("<==== compiling hello.h ====>")) == 2
    }

    def "compiler arguments set on the binary get used for the precompiled header" () {
        given:
        standardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()
        buildFile << """
            model {
                components {
                    hello {
                        binaries.all {
                            ${app.compilerDefine("FRENCH")}
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ], "<==== compiling bonjour.h ====>")

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibPCHNotCompiled()
        ! output.contains("<==== compiling bonjour.h ====>")
    }

    def "precompiled header compile detects changes in header files" () {
        given:
        standardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ])

        when:
        alternateLibraryHeader.writeToDir(file("src/hello"))
        maybeWait()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        executedAndNotSkipped ":${sharedPCHCompileTaskName}", ":${sharedLibraryCompileTaskName}"
        skipped ":${generatePrefixHeaderTaskName}"
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ], "<==== compiling althello.h ====>")
    }

    def "produces warning when pch cannot be used" () {
        given:
        app.getLibraryHeader().writeToDir(file("src/hello"))
        def helloDotC = app.getLibrarySources().find { it.name.startsWith("hello") }.writeToDir(file("src/hello"))
        helloDotC.text = "#include \"hello.h\"\n" + helloDotC.text
        app.commonHeader.writeToDir(file("src/hello"))

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        succeeds "helloSharedLibrary"
        sharedLibAndPCHTasksExecuted()
        pchCompiledOnceForEach([ sharedPCHHeaderDirName ])
        output.contains("The source file hello.${app.sourceExtension} includes the header common.h but it is not the first declared header, so the pre-compiled header will not be used.")
    }

    def "produces compiler error when specified header is missing" () {
        given:
        app.getLibraryHeader().writeToDir(file("src/hello"))
        app.getLibrarySources().find { it.name.startsWith("hello") }.writeToDir(file("src/hello"))
        assert ! file("src/hello/headers/prefixHeader.h").exists()

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        fails "helloSharedLibrary"
        failure.assertHasDescription("Execution failed for task ':${sharedPCHCompileTaskName}'.")
        failure.assertThatCause(Matchers.containsString("compiler failed while compiling prefix-headers"))
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can build and run an executable with library using pch" () {
        given:
        standardSourceFiles()
        app.mainSource.writeToDir(file("src/main"))

        when:
        buildFile << """
            $mainComponent
            ${preCompiledHeaderComponent()}
        """

        then:
        succeeds "installMainExecutable"
        sharedLibAndPCHTasksExecuted()

        and:
        def install = installation("build/install/mainExecutable")
        install.assertInstalled()
        install.exec().out == app.englishOutput
    }

    def preCompiledHeaderComponent(String path="") {
        """
            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            ${app.sourceType}.preCompiledHeader "${path}common.h"
                        }
                        binaries.all {
                            if (toolChain.name == "visualCpp") {
                                ${app.compilerArgs("/showIncludes")}
                            } else {
                                ${app.compilerArgs("-H")}
                            }
                        }
                    }
                }
            }
        """
    }

    def standardSourceFiles(path="") {
        app.libraryHeader.writeToDir(file("src/hello"))
        getLibrarySources(path).each { it.writeToDir(file("src/hello")) }
        getCommonHeader(path).writeToDir(file("src/hello"))
        assert file("src/hello/headers/${path}common.h").exists()
    }

    def librarySourceModified(path="") {
        getAlternateLibrarySources(path).find { it.name == "hello.${app.sourceExtension}" }.writeToDir(file("src/hello"))
        maybeWait()
    }

    def sharedLibAndPCHTasksExecuted() {
        libAndPCHtasksExecuted(sharedPCHCompileTaskName, sharedLibraryCompileTaskName)
    }

    def staticLibAndPCHTasksExecuted() {
        libAndPCHtasksExecuted(staticPCHCompileTaskName, staticLibraryCompileTaskName)
    }

    def libAndPCHtasksExecuted(pchCompileTask, compileTask) {
        executedAndNotSkipped ":${pchCompileTask}", ":${generatePrefixHeaderTaskName}", ":${compileTask}"
        true
    }

    def pchCompiledOnceForEach(List pchDirs, message="<==== compiling hello.h ====>") {
        output.count(getUniquePragmaOutput(message)) == pchDirs.size()
        pchDirs.each { pchHeaderDirName ->
            def outputDirectories = file(pchHeaderDirName).listFiles().findAll { it.isDirectory() }
            assert outputDirectories.size() == 1
            assert outputDirectories[0].assertContainsDescendants("prefix-headers.${getSuffix()}")
        }
        true
    }

    def sharedLibPCHNotCompiled(message="<==== compiling hello.h ====>") {
        pchNotCompiled(sharedPCHCompileTaskName, sharedLibraryCompileTaskName, message)
    }

    def staticLibPCHNotCompiled(message="<==== compiling hello.h ====>") {
        pchNotCompiled(staticPCHCompileTaskName, staticLibraryCompileTaskName, message)
    }

    def pchNotCompiled(pchCompileTask, compileTask, message) {
        executedAndNotSkipped ":${compileTask}"
        skipped ":${pchCompileTask}", ":${generatePrefixHeaderTaskName}"
        output.count(getUniquePragmaOutput(message)) == 0
        true
    }

    private void maybeWait() {
        if (toolChain.visualCpp) {
            def now = System.currentTimeMillis()
            def nextSecond = now % 1000
            Thread.sleep(1200 - nextSecond)
        }
    }

    String getSuffix() {
        return toolChain.displayName == "visual c++" ? "pch" : "h.gch"
    }

    String getUniquePragmaOutput(String message) {
        if (toolChain.displayName == "clang") {
            return "warning: ${message}"
        } else if (toolChain.displayName.startsWith("gcc") || toolChain.displayName == "mingw") {
            return "message: ${message}"
        } else {
            return message
        }
    }

    List<SourceFile> getLibrarySources(String headerPath) {
        updateCommonHeaderPath(app.getLibrarySources(), headerPath)
    }

    List<SourceFile> getAlternateLibrarySources(String headerPath) {
        updateCommonHeaderPath(app.getAlternateLibrarySources(), headerPath)
    }

    SourceFile getCommonHeader(String path) {
        updateSourceFilePath(app.getCommonHeader(), path)
    }

    SourceFile getAlternateLibraryHeader() {
        modifySourceFile(app.getLibraryHeader(), "compiling hello.h", "compiling althello.h")
    }

    SourceFile getLibraryWithoutPCH() {
        def original = app.getLibrarySources().find { it.name == "sum.${app.sourceExtension}" }
        modifySourceFile(original, "include \"common.h\"", "include \"hello.h\"")
    }

    static List<SourceFile> updateCommonHeaderPath(List<SourceFile> sourceFiles, String headerPath) {
        return sourceFiles.collect {
            def newContent = it.content.replaceAll("#include \"common.h\"", "#include \"${headerPath}common.h\"")
            new SourceFile(it.path, it.name, newContent)
        }
    }

    static SourceFile modifySourceFile(SourceFile sourceFile, String text, String replacement) {
        new SourceFile(sourceFile.path, sourceFile.name, sourceFile.content.replaceAll(text, replacement))
    }

    static SourceFile updateSourceFilePath(SourceFile sourceFile, String path) {
        new SourceFile("${sourceFile.path}/${path}", sourceFile.name, sourceFile.content)
    }

    String getSharedPCHCompileTaskName() {
        return "compileHelloSharedLibrary${StringUtils.capitalize(app.sourceType)}PreCompiledHeader"
    }

    String getStaticPCHCompileTaskName() {
        return "compileHelloStaticLibrary${StringUtils.capitalize(app.sourceType)}PreCompiledHeader"
    }

    String getGeneratePrefixHeaderTaskName() {
        return "generate${StringUtils.capitalize(app.sourceType)}PrefixHeaderFile"
    }

    String getSharedLibraryCompileTaskName() {
        return "compileHelloSharedLibraryHello${StringUtils.capitalize(app.sourceType)}"
    }

    String getStaticLibraryCompileTaskName() {
        return "compileHelloStaticLibraryHello${StringUtils.capitalize(app.sourceType)}"
    }

    String getSharedPCHHeaderDirName() {
        return "build/objs/helloSharedLibrary/hello${StringUtils.capitalize(app.sourceType)}PCH"
    }

    String getStaticPCHHeaderDirName() {
        return "build/objs/helloStaticLibrary/hello${StringUtils.capitalize(app.sourceType)}PCH"
    }

    String getMainComponent() {
        return """
            model {
                components {
                    main(NativeExecutableSpec) {
                        sources {
                            ${app.sourceType}.lib library: "hello"
                        }
                    }
                }
            }
        """
    }
}
