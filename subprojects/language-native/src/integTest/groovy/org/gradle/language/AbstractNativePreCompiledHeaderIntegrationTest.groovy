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
import org.hamcrest.CoreMatchers
import org.spockframework.util.TextUtil

abstract class AbstractNativePreCompiledHeaderIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    abstract IncrementalHelloWorldApp getApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
    }

    def "clean build with PCH does not fail"() {
        given:
        writeStandardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])

        expect:
        succeeds("clean", "helloSharedLibrary")
    }

    def "can set a precompiled header on a source set for a source header in the headers directory" () {
        given:
        writeStandardSourceFiles(path)

        when:
        buildFile << preCompiledHeaderComponent(path)

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])

        when:
        librarySourceModified("hello", path)

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        pchNotCompiled()

        where:
        path << [ "", "subdir/to/header/" ]
    }

    def "can set a precompiled header on a source set for a header colocated with the source" () {
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
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        pchNotCompiled()
    }

    def "can set a precompiled header on a source set for a source header in include path" () {
        given:
        app.libraryHeader.writeToDir(file("src/include"))
        getLibrarySources(path).each { it.writeToDir(file("src/hello")) }
        getCommonHeader(path).writeToDir(file("src/include"))

        when:
        def headerDir = file("src/include/headers")
        def safeHeaderDirPath = TextUtil.escape(headerDir.absolutePath)
        buildFile << preCompiledHeaderComponent(path, pch)
        buildFile << """
            model {
                components {
                    hello {
                        sources.all {
                            exportedHeaders {
                                srcDir "${safeHeaderDirPath}"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])

        when:
        librarySourceModified("hello", path)

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        pchNotCompiled()

        where:
        path      | pch
        ""        | "common.h"
        "subdir/" | "common.h"
        ""        | "<common.h>"
    }

    def "a precompiled header on a source set gets used for all variants of a binary" () {
        given:
        writeStandardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "assemble"
        libAndPCHTasksExecuted("hello", "shared")
        libAndPCHTasksExecuted("hello", "static")
        pchCompiledOnceForEach([ getPCHHeaderDirName("hello", "shared"), getPCHHeaderDirName("hello", "static") ])

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "assemble"
        pchNotCompiled("hello", "shared")
        pchNotCompiled("hello", "static")
    }

    def "can set a precompiled header on multiple source sets" () {
        given:
        app.headerFiles.each { it.writeToDir(file("src/hello")) }
        app.librarySources.find { it.name == "hello.${app.sourceExtension}" }.writeToDir(file("src/hello"))
        writeOtherSourceSetFiles()

        when:
        buildFile << preCompiledHeaderComponent()
        buildFile << """
            model {
                components {
                    hello {
                        sources {
                            other(${app.sourceSetType}) {
                                preCompiledHeader "common2.h"
                            }
                        }
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted("hello", "shared", "${app.sourceType}")
        libAndPCHTasksExecuted("hello", "shared", "other")
        pchCompiledOnceForEach([ PCHHeaderDirName, getPCHHeaderDirName("hello", "shared", "other") ])

        when:
        otherLibrarySourceModified()
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        pchNotCompiled("hello", "shared", "${app.sourceType}")
        pchNotCompiled("hello", "shared", "other")
    }

    def "can set a precompiled header on multiple components" () {
        given:
        writeStandardSourceFiles()
        app.library.writeSources(file("src/hello2"))

        when:
        buildFile << preCompiledHeaderComponent()
        buildFile << """
            model {
                components {
                    hello2(NativeLibrarySpec) {
                        sources.${app.sourceType}.preCompiledHeader "common.h"
                    }
                }
            }
        """

        then:
        args("--info")
        succeeds "helloSharedLibrary", "hello2SharedLibrary"
        libAndPCHTasksExecuted("hello")
        libAndPCHTasksExecuted("hello2")
        pchCompiledOnceForEach([ getPCHHeaderDirName("hello", "shared"), getPCHHeaderDirName("hello2", "shared") ])

        when:
        librarySourceModified("hello")
        librarySourceModified("hello2")

        then:
        args("--info")
        succeeds "helloSharedLibrary", "hello2SharedLibrary"
        pchNotCompiled("hello")
        pchNotCompiled("hello2")
    }

    def "can have components both with and without precompiled headers" () {
        given:
        writeStandardSourceFiles()
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
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        pchNotCompiled()

        and:
        args("--info")
        succeeds "hello2SharedLibrary"
        executedAndNotSkipped ":${getLibraryCompileTaskName("hello2", "shared")}"
        notExecuted ":${getGeneratePrefixHeaderTaskName("hello")}", ":${getPCHCompileTaskName("hello", "shared")}"
        // once for hello2.c only
        output.count(getUniquePragmaOutput(DEFAULT_PCH_MESSAGE)) == 1
    }

    def "can have sources that do not use precompiled header" () {
        given:
        writeStandardSourceFiles()
        libraryWithoutPCH.writeToDir(file("src/hello"))

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
        // once for PCH, once for source file without PCH
        output.count(getUniquePragmaOutput(DEFAULT_PCH_MESSAGE)) == 2
    }

    def "compiler arguments set on the binary get used for the precompiled header" () {
        given:
        writeStandardSourceFiles()

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
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ], FRENCH_PCH_MESSAGE)

        when:
        librarySourceModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        pchNotCompiled()
        ! output.contains(FRENCH_PCH_MESSAGE)
    }

    def "precompiled header compile detects changes in header files" () {
        given:
        writeStandardSourceFiles()

        when:
        buildFile << preCompiledHeaderComponent()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
        pchCompiledOnceForEach([ PCHHeaderDirName ])

        when:
        libraryHeaderModified()

        then:
        args("--info")
        succeeds "helloSharedLibrary"
        executedAndNotSkipped ":${getPCHCompileTaskName("hello", "shared")}", ":${getLibraryCompileTaskName("hello", "shared")}"
        skipped ":${getGeneratePrefixHeaderTaskName("hello")}"
        pchCompiledOnceForEach([ PCHHeaderDirName ], ALTERNATE_PCH_MESSAGE)
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
        args("--info")
        succeeds "helloSharedLibrary"
        libAndPCHTasksExecuted()
        // Once for PCH compile, once for hello.c
        output.count(getUniquePragmaOutput(DEFAULT_PCH_MESSAGE)) == 2
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
        failure.assertHasDescription("Execution failed for task ':${getPCHCompileTaskName("hello", "shared")}'.")
        failure.assertThatCause(CoreMatchers.containsString("compiler failed while compiling prefix-headers"))
    }

    @Requires(TestPrecondition.CAN_INSTALL_EXECUTABLE)
    def "can build and run an executable with library using pch" () {
        given:
        writeStandardSourceFiles()
        app.mainSource.writeToDir(file("src/main"))

        when:
        buildFile << """
            $mainComponent
            ${preCompiledHeaderComponent()}
        """

        then:
        succeeds "installMainExecutable"
        libAndPCHTasksExecuted()

        and:
        def install = installation("build/install/main")
        install.assertInstalled()
        install.exec().out == app.englishOutput
    }

    static final String DEFAULT_PCH_MESSAGE="<==== compiling hello.h ====>"
    static final String FRENCH_PCH_MESSAGE="<==== compiling bonjour.h ====>"
    static final String ALTERNATE_PCH_MESSAGE="<==== compiling alternate hello.h ====>"

    def preCompiledHeaderComponent(String path="", String pch="common.h") {
        """
            model {
                components {
                    hello(NativeLibrarySpec) {
                        sources {
                            ${app.sourceType}.preCompiledHeader "${path}${pch}"
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

    def writeStandardSourceFiles(path="") {
        app.libraryHeader.writeToDir(file("src/hello"))
        getLibrarySources(path).each { it.writeToDir(file("src/hello")) }
        getCommonHeader(path).writeToDir(file("src/hello"))
        assert file("src/hello/headers/${path}common.h").exists()
    }

    def writeOtherSourceSetFiles() {
        def sumSourceFile = app.librarySources.find { it.name == "sum.${app.sourceExtension}" }
        replaceInSourceFile(toOtherSourceSet(sumSourceFile), 'include "common.h"', 'include "common2.h"').writeToDir(file("src/hello"))
        renameSourceFile(app.commonHeader, "common2.h").writeToDir(file("src/hello"))
    }

    def toOtherSourceSet(SourceFile sourceFile) {
        return new SourceFile("other", sourceFile.name, sourceFile.content)
    }

    def renameSourceFile(SourceFile sourceFile, String name) {
        return new SourceFile(sourceFile.path, name, sourceFile.content)
    }

    def addFunction(SourceFile sourceFile) {
        return new SourceFile(sourceFile.path, sourceFile.name, sourceFile.content + """
            // Extra function to ensure library has different size
            int otherFunction() {
                return 1000;
            }
        """)
    }

    def otherLibrarySourceModified() {
        def sumSourceFile = app.alternateLibrarySources.find { it.name == "sum.${app.sourceExtension}" }
        def alternateSumFile = addFunction(sumSourceFile)
        replaceInSourceFile(toOtherSourceSet(alternateSumFile), 'include "common.h"', 'include "common2.h"').writeToDir(file("src/hello"))
    }

    def librarySourceModified(String lib="hello", String path="") {
        getAlternateLibrarySources(path).find { it.name == "hello.${app.sourceExtension}" }.writeToDir(file("src/${lib}"))
        maybeWait()
    }

    def libraryHeaderModified() {
        alternateLibraryHeader.writeToDir(file("src/hello"))
        maybeWait()
    }

    def libAndPCHTasksExecuted(String lib="hello", String linkage="shared", String sourceSet=app.sourceType) {
        executedAndNotSkipped(":${getPCHCompileTaskName(lib, linkage, sourceSet)}", ":${getLibraryCompileTaskName(lib, linkage, sourceSet)}", ":${getGeneratePrefixHeaderTaskName(lib, sourceSet)}")
        true
    }

    def pchCompiledOnceForEach(List pchDirs, message=DEFAULT_PCH_MESSAGE) {
        assert output.count(getUniquePragmaOutput(message)) == pchDirs.size()
        pchDirs.each { pchHeaderDirName ->
            def outputDirectories = file(pchHeaderDirName).listFiles().findAll { it.isDirectory() }
            assert outputDirectories.size() == 1
            outputDirectories[0].assertContainsDescendants("prefix-headers.${getSuffix()}")
        }
        true
    }

    def pchNotCompiled(String lib="hello", String linkage="shared", String sourceSet=app.sourceType) {
        def pchCompileTask = getPCHCompileTaskName(lib, linkage)
        def compileTask = getLibraryCompileTaskName(lib, linkage, sourceSet)
        def generateTask = getGeneratePrefixHeaderTaskName(lib, sourceSet)
        executedAndNotSkipped ":${compileTask}"
        skipped ":${pchCompileTask}", ":${generateTask}"
        assert output.count(getUniquePragmaOutput(DEFAULT_PCH_MESSAGE)) == 0
        true
    }

    String getSuffix() {
        return toolChain.typeDisplayName == "visual c++" ? "pch" : "h.gch"
    }

    String getUniquePragmaOutput(String message) {
        if (toolChain.displayName.startsWith("clang")) {
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

    SourceFile getCommonHeader(String path = "") {
        updateSourceFilePath(app.getCommonHeader(), path)
    }

    SourceFile getAlternateLibraryHeader() {
        replaceInSourceFile(app.getLibraryHeader(), "compiling hello.h", "compiling alternate hello.h")
    }

    SourceFile getLibraryWithoutPCH() {
        def original = app.getLibrarySources().find { it.name == "sum.${app.sourceExtension}" }
        replaceInSourceFile(original, "include \"common.h\"", "include \"hello.h\"")
    }

    static List<SourceFile> updateCommonHeaderPath(List<SourceFile> sourceFiles, String headerPath) {
        return sourceFiles.collect {
            def newContent = it.content.replaceAll("#include \"common.h\"", "#include \"${headerPath}common.h\"")
            new SourceFile(it.path, it.name, newContent)
        }
    }

    static SourceFile replaceInSourceFile(SourceFile sourceFile, String text, String replacement) {
        new SourceFile(sourceFile.path, sourceFile.name, sourceFile.content.replaceAll(text, replacement))
    }

    static SourceFile updateSourceFilePath(SourceFile sourceFile, String path) {
        new SourceFile("${sourceFile.path}/${path}", sourceFile.name, sourceFile.content)
    }

    String getPCHCompileTaskName(String lib, String linkage, String sourceSet=app.sourceType) {
        return "compile${StringUtils.capitalize(lib)}${StringUtils.capitalize(linkage)}Library${StringUtils.capitalize(sourceSet)}PreCompiledHeader"
    }

    String getGeneratePrefixHeaderTaskName(String lib, String sourceSet=app.sourceType) {
        return "generate${StringUtils.capitalize(lib)}${StringUtils.capitalize(sourceSet)}PrefixHeaderFile"
    }

    String getLibraryCompileTaskName(String lib, String linkage, String sourceSet=app.sourceType) {
        return "compile${StringUtils.capitalize(lib)}${StringUtils.capitalize(linkage)}Library${StringUtils.capitalize(lib)}${StringUtils.capitalize(sourceSet)}"
    }

    String getPCHHeaderDirName(String lib="hello", String linkage="shared", String sourceSet=app.sourceType) {
        return "build/objs/${lib}/${linkage}/${lib}${StringUtils.capitalize(sourceSet)}PCH"
    }
}
