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
import groovy.io.FileType
import org.apache.commons.io.FilenameUtils
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GUtil

abstract class AbstractLanguageIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    IncrementalHelloWorldApp app
    String compileTask
    TestFile sourceFile
    TestFile sharedHeaderFile
    TestFile otherHeaderFile
    List<TestFile> otherSourceFiles = []

    abstract IncrementalHelloWorldApp getHelloWorldApp();

    String getSourceType() {
        GUtil.toCamelCase(app.sourceType)
    }

    def "setup"() {
        app = getHelloWorldApp()
        compileTask = ":compileMainExecutableMain${sourceType}"

        buildFile << app.pluginScript
        buildFile << """
            executables {
                main {}
            }
        """

        and:
        sourceFile = app.mainSource.writeToDir(file("src/main"))
        sharedHeaderFile = app.libraryHeader.writeToDir(file("src/main"))
        app.librarySources.each {
            otherSourceFiles << it.writeToDir(file("src/main"))
        }
        otherHeaderFile = file("src/main/headers/other.h") << """
            // Dummy header file
"""
    }

    def "recompiles changed source file only"() {
        given:
        initialCompile()

        when:
        sourceFile << """
// Changed source file
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile
    }

    def "recompiles all source files that include changed header file"() {
        given:
        initialCompile()

        when:
        sharedHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled allSources
    }

    def "recompiles only source file that includes changed header file"() {
        given:
        sourceFile << """
            #include "${otherHeaderFile.name}"
"""
        and:
        initialCompile()

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile
    }

    def "recompiles source file when transitively included header file is changed"() {
        given:
        def transitiveHeaderFile = file("src/main/headers/transitive.h") << """
           // Dummy header file
"""
        otherHeaderFile << """
            #include "${transitiveHeaderFile.name}"
"""
        sourceFile << """
            #include "${otherHeaderFile.name}"
"""

        and:
        initialCompile()

        when:
        transitiveHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled sourceFile
    }

    def "does not recompile any sources when unused header file is changed"() {
        given:
        initialCompile()

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        skipped compileTask
        noneRecompiled()
    }

    def "recompiles all source files and removes stale outputs when compiler arg changes"() {
        given:
        def extraSource = file("src/main/${app.sourceType}/extra.${app.sourceType}")
        extraSource << sourceFile.text.replaceAll("main", "main2")

        initialCompile()

        outputFile(extraSource).assertExists()

        when:
        sourceFile << """
            // Changed source file
"""
        buildFile << """
            executables {
                main {
                    binaries.all {
                        ${helloWorldApp.compilerDefine("MY_DEF")}
                    }
                }
            }
"""
        extraSource.delete()

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled allSources
        outputFile(extraSource).assertDoesNotExist()
    }

    def "removes output file when source file is renamed"() {
        given:
        initialCompile()

        when:
        final newFile = file("src/main/${app.sourceType}/changed.${app.sourceType}")
        newFile << sourceFile.text
        sourceFile.delete()

        and:
        run "mainExecutable"

        then:
        recompiled newFile
        outputFile(sourceFile).assertDoesNotExist()
    }

    def "removes output file when source file is removed"() {
        given:
        def extraSource = file("src/main/${app.sourceType}/extra.${app.sourceType}")
        extraSource << sourceFile.text.replaceAll("main", "main2")

        initialCompile()

        and:
        outputFile(extraSource).assertExists()

        when:
        extraSource.delete()

        and:
        run "mainExecutable"

        then:
        outputFile(extraSource).assertDoesNotExist()
        noneRecompiled()
    }

    def "removes all output files when all source files are removed"() {
        given:
        initialCompile()

        def executable = executable("build/binaries/mainExecutable/main")
        executable.assertExists()

        when:
        file("src/main").eachFileRecurse(FileType.FILES) {
            println "deleting ${it}"
            it.delete()
        }

        and:
        run "mainExecutable"

        then:
        file("build/objectFiles").assertIsEmptyDir()

        // TODO:DAZ Link task should remove output when inputs are all removed
//        executable.assertDoesNotExist()
    }

    def initialCompile() {
        run "mainExecutable"

        // Set the last modified timestamp to zero for all object files
        file("build/objectFiles").eachFileRecurse(FileType.FILES) { it.lastModified = 0 }
    }

    def getRecompiledFiles() {
        // Get all of the object files that do not have a zero last modified timestamp
        def recompiled = []
        file("build/objectFiles").eachFileRecurse(FileType.FILES) {
            if (it.lastModified() > 0) {
                recompiled << FilenameUtils.removeExtension(it.name)
            }
        }
        return recompiled as Set
    }

    def getAllSources() {
        return [sourceFile] + otherSourceFiles
    }

    def noneRecompiled() {
        recompiled([])
    }

    def recompiled(TestFile file) {
        recompiled([file])
    }

    def recompiled(List<TestFile> files) {
        def expectedNames = files.collect({ FilenameUtils.removeExtension(it.name) }) as Set
        assert getRecompiledFiles() == expectedNames
        return true
    }

    def outputFile(TestFile sourceFile) {
        final baseName = FilenameUtils.removeExtension(sourceFile.name)
        return objectFile("build/objectFiles/mainExecutable/main${sourceType}/${baseName}")
    }
}
