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
import org.gradle.internal.hash.HashUtil
import org.gradle.nativebinaries.language.cpp.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativebinaries.language.cpp.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GUtil
import spock.lang.Unroll

abstract class AbstractLanguageIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    IncrementalHelloWorldApp app
    String compileTask
    TestFile sourceFile
    TestFile sharedHeaderFile
    TestFile otherHeaderFile
    List<TestFile> otherSourceFiles = []
    TestFile objectFileDir

    abstract IncrementalHelloWorldApp getHelloWorldApp();

    String getSourceType() {
        GUtil.toCamelCase(app.sourceType)
    }

    def "setup"() {
        app = getHelloWorldApp()
        compileTask = ":compileMainExecutableMain${sourceType}"

        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
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
        objectFileDir = file("build/objectFiles/mainExecutable")
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

    def "source is always recompiled if it includes header via macro"() {
        given:
        sourceFile << """
            #define MY_HEADER "${otherHeaderFile.name}"
            #include MY_HEADER
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

        // TODO:DAZ Remove this behaviour
        when: "Header that is NOT included is changed"
        file("src/main/headers/notIncluded.h") << """
            // Dummy header file
"""
        and:
        run "mainExecutable"

        then: "Source is still recompiled"
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

    def "recompiles source file when an included header file is renamed"() {
        given:
        initialCompile()

        and:
        final newFile = file("src/main/headers/changed.h")
        newFile << sharedHeaderFile.text
        sharedHeaderFile.delete()

        when:
        fails "mainExecutable"

        then:
        executedAndNotSkipped compileTask
        failure.assertHasDescription("Execution failed for task '${compileTask}'.");
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

    @Unroll
    def "does not recompile when include path has #testCase"() {
        given:
        initialCompile()

        file("src/additional-headers/other.h") << """
    // extra header file that is not included in source
"""
        file("src/replacement-headers/${sharedHeaderFile.name}") << """
    // replacement header file that is included in source
"""

        when:
        buildFile << """
            sources.main.${app.sourceType} {
                exportedHeaders {
                    srcDirs ${headerDirs}
                }
            }
"""
        and:
        run "mainExecutable"

        then:
        skipped compileTask
        noneRecompiled()

        where:
        testCase                       | headerDirs
        "extra header dir after"       | '"src/main/headers", "src/additional-headers"'
        "extra header dir before"      | '"src/additional-headers", "src/main/headers"'
        "replacement header dir after" | '"src/main/headers", "src/replacement-headers"'
    }

    def "recompiles when include path is changed so that replacement header file occurs before previous header"() {
        given:
        initialCompile()

        file("src/replacement-headers/${sharedHeaderFile.name}") << sharedHeaderFile.text

        when:
        buildFile << """
            sources.main.${app.sourceType}  {
                exportedHeaders {
                    srcDirs "src/replacement-headers", "src/main/headers"
                }
            }
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled allSources
    }

    def "recompiles when replacement header file is added before previous header to existing include path"() {
        given:
        buildFile << """
            sources.main.${app.sourceType} {
                exportedHeaders {
                    srcDirs "src/replacement-headers", "src/main/headers"
                }
            }
"""
        initialCompile()

        when:
        file("src/replacement-headers/${sharedHeaderFile.name}") << sharedHeaderFile.text

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled allSources
    }

    def "recompiles when replacement header file is added to source directory"() {
        given:
        initialCompile()

        when:
        sourceFile.parentFile.file(sharedHeaderFile.name) << sharedHeaderFile.text

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled allSources
    }

    def "recompiles all source files and removes stale outputs when compiler arg changes"() {
        given:
        def extraSource = file("src/main/${app.sourceType}/extra.${app.sourceExtension}")
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

    def "recompiles all source files when generated object files are removed"() {
        given:
        initialCompile()

        when:
        objectFileDir.deleteDir()
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        recompiled allSources
    }

    def "removes output file when source file is renamed"() {
        given:
        initialCompile()

        when:
        final newFile = file("src/main/${app.sourceType}/changed.${app.sourceExtension}")
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
        def extraSource = file("src/main/${app.sourceType}/extra.${app.sourceExtension}")
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

    def "removes output files when all source files are removed"() {
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

        then: "linker output file is removed"
        executable.assertDoesNotExist()

        // Stale object files are removed when a new file is added to the source set
        when:
        def newSource = file("src/main/${app.sourceType}/newfile.${app.sourceExtension}") << """
            #include <stdio.h>

            int main () {
                printf("hello");
                return 0;
            }
"""

        and:
        run "mainExecutable"

        then:
        executable.exec().out == "hello"
        outputFile(newSource).assertExists()

        and: "Previous object files are removed"
        outputFile(sourceFile).assertDoesNotExist()
        otherSourceFiles.each {
            outputFile(it).assertDoesNotExist()
        }
    }

    def "incremental compile is not effected by other compile tasks"() {
        given:
        buildFile << """
            executables {
                other
            }
"""
        app.writeSources(file("src/other"))

        and:
        initialCompile()

        and:
        // Completely independent compile task (state should be independent)
        run "otherExecutable"

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


    def initialCompile() {
        run "mainExecutable"

        // Set the last modified timestamp to zero for all object files
        objectFileDir.eachFileRecurse(FileType.FILES) { it.lastModified = 0 }
    }

    def getRecompiledFiles() {
        // Get all of the object files that do not have a zero last modified timestamp
        def recompiled = []
        objectFileDir.eachFileRecurse(FileType.FILES) {
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
        String compactMD5 = HashUtil.createCompactMD5(sourceFile.getAbsolutePath());
        return objectFile("build/objectFiles/mainExecutable/main${sourceType}/$compactMD5/${baseName}")
    }
}
