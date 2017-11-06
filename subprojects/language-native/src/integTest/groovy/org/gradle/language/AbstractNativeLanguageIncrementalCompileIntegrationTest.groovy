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

package org.gradle.language

import groovy.io.FileType
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.IncrementalHelloWorldApp
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GUtil

abstract class AbstractNativeLanguageIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    IncrementalHelloWorldApp app
    String compileTask
    TestFile sourceFile
    TestFile sharedHeaderFile
    TestFile commonHeaderFile
    TestFile otherHeaderFile
    List<TestFile> otherSourceFiles = []
    TestFile objectFileDir
    CompilationOutputsFixture outputs

    abstract IncrementalHelloWorldApp getHelloWorldApp()

    String getSourceType() {
        GUtil.toCamelCase(app.sourceType)
    }

    def "setup"() {
        app = getHelloWorldApp()
        compileTask = ":compileMainExecutableMain${sourceType}"

        buildFile << app.pluginScript
        buildFile << app.extraConfiguration
        buildFile << """
    model {
        components {
            main(NativeExecutableSpec)
        }
    }
        """

        and:
        sourceFile = app.mainSource.writeToDir(file("src/main"))
        sharedHeaderFile = app.libraryHeader.writeToDir(file("src/main"))
        commonHeaderFile = app.commonHeader.writeToDir(file("src/main"))
        app.librarySources.each {
            otherSourceFiles << it.writeToDir(file("src/main"))
        }
        otherHeaderFile = file("src/main/headers/other.h") << """
            // Dummy header file
"""
        objectFileDir = file("build/objs/main")
        outputs = new CompilationOutputsFixture(objectFileDir)
    }

    def "recompiles changed source file only"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        when:
        sourceFile << """
// Changed source file
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }

    def "recompiles all source files that include changed header file"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        when:
        sharedHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFiles allSources
    }

    def "recompiles only source file that includes changed header file"() {
        given:
        sourceFile << """
            #include "${otherHeaderFile.name}"
"""
        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }

    def "does not recompile when fallback mechanism is used and there are empty directories"() {
        given:
        file("src/main/headers/empty/directory").mkdirs()
        sourceFile << """
            #define MY_HEADER "${otherHeaderFile.name}"
            #include MY_HEADER
"""

        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        run "mainExecutable"
        then:
        skipped compileTask
    }

    def "does not recompile when included header has the same name as a directory"() {
        given:
        buildFile << """
model {
    components {
        main {
            sources.all {
                exportedHeaders {
                    srcDirs = [ "src/other", "src/main/headers" ]
                }
            }
        }
    }
}
"""
        // This is a directory named 'directory'
        file("src/other/directory").mkdirs()
        // This is a header named 'directory'
        file("src/main/headers/directory") << '#pragma message("including directory named header")'
        file("src/main/headers/macro.h") << '#pragma message("including macro header")'

        sourceFile << """
            #include "directory"
            #define MACRO "macro.h"
            #include MACRO
"""

        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        run "mainExecutable"

        then:
        executed compileTask
        skipped compileTask
    }

    def "recompiles when included header has the same name as a directory and the directory becomes a file"() {
        given:
        buildFile << """
model {
    components {
        main {
            sources.all {
                exportedHeaders {
                    srcDirs = [ "src/other", "src/main/headers" ]
                }
            }
        }
    }
}
"""
        // directory header starts out as a directory
        def directoryHeader = file("src/other/directory")
        directoryHeader.mkdirs()
        // this is the a header file named 'directory'
        file("src/main/headers/directory") << '#pragma message("including directory named header")'

        sourceFile << """
            #include "directory"
"""

        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        directoryHeader.deleteDir()
        directoryHeader << '#pragma message("NEW directory named header")'
        and:
        executer.withArgument("--info")
        run "mainExecutable"
        then:
        executedAndNotSkipped compileTask
        and:
        outputs.recompiledFile sourceFile
        result.assertOutputContains("NEW directory named header")

    }

    def "source is always recompiled if it includes header via macro"() {
        given:
        def notIncluded = file("src/main/headers/notIncluded.h")
        notIncluded.text = """#pragma message("should not be used")"""
        sourceFile << """
            #define MY_HEADER "${otherHeaderFile.name}"
            #include MY_HEADER
"""

        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile

        when: "Header that is NOT included is changed"
        notIncluded << """
            // Dummy header file
"""
        and:
        run "mainExecutable"

        then: "Source is still recompiled"
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }

    def "source is not recompiled when preprocessor removed header is changed"() {
        given:
        def notIncluded = file("src/main/headers/notIncluded.h")
        notIncluded.text = """#pragma message("should not be used")"""
        sourceFile << """
            #if 0
            #include "${notIncluded.name}"
            #else
            #include "${otherHeaderFile.name}"
            #endif
"""
        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
        and:
        !output.contains("should not be used")

        when:
        // this header isn't included
        notIncluded << """
            // Dummy header file
"""
        and:
        run "mainExecutable"

        then:
        // TODO: This is inefficient behavior, we should skip the compile task because 'notIncluded' is not used.
        // skipped compileTask
        executedAndNotSkipped compileTask
    }

    def "source is compiled when preprocessor removed header does not exist"() {
        given:
        sourceFile << """
            #if 0
            #include "doesNotExist.h"
            #else
            #include "${otherHeaderFile.name}"
            #endif
"""
        and:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile

        when:
        run "mainExecutable"

        then:
        skipped compileTask
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
        outputs.snapshot { run "mainExecutable" }

        when:
        transitiveHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFile sourceFile
    }

    def "recompiles source file when an included header file is renamed"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        and:
        final newFile = file("src/main/headers/changed.h")
        newFile << sharedHeaderFile.text
        sharedHeaderFile.delete()

        when:
        fails "mainExecutable"

        then:
        executedAndNotSkipped compileTask
        failure.assertHasDescription("Execution failed for task '${compileTask}'.")
    }

    def "does not recompile any sources when unused header file is changed"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        when:
        otherHeaderFile << """
            // Some extra content
"""
        and:
        run "mainExecutable"

        then:
        executed compileTask
        skipped compileTask

        and:
        outputs.noneRecompiled()
    }

    def "recompiles when include path is changed so that replacement header file occurs before previous header"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        file("src/replacement-headers/${sharedHeaderFile.name}") << sharedHeaderFile.text << "\n"
        file("src/replacement-headers/${commonHeaderFile.name}") << commonHeaderFile.text << "\n"

        when:
        buildFile << """
model {
    components {
        main {
            sources {
                ${app.sourceType}  {
                    exportedHeaders {
                        srcDirs "src/replacement-headers", "src/main/headers"
                    }
                }
            }
        }
    }
}
"""
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFiles allSources
    }

    def "recompiles when replacement header file is added before previous header to existing include path"() {
        given:
        buildFile << """
model {
    components {
        main {
            sources {
                ${app.sourceType} {
                    exportedHeaders {
                        srcDirs "src/replacement-headers", "src/main/headers"
                    }
                }
            }
        }
    }
}
"""
        outputs.snapshot { run "mainExecutable" }

        when:
        file("src/replacement-headers/${sharedHeaderFile.name}") << sharedHeaderFile.text << "\n"
        file("src/replacement-headers/${commonHeaderFile.name}") << commonHeaderFile.text << "\n"

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFiles allSources
    }

    def "recompiles when replacement header file is added to source directory"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        when:
        sourceFile.parentFile.file(sharedHeaderFile.name) << sharedHeaderFile.text
        sourceFile.parentFile.file(commonHeaderFile.name) << commonHeaderFile.text

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFiles allSources + [commonHeaderFile]
    }

    def "recompiles all source files and removes stale outputs when compiler arg changes"() {
        given:
        def extraSource = file("src/main/${app.sourceType}/extra.${app.sourceExtension}")
        extraSource << sourceFile.text.replaceAll("main", "main2")

        outputs.snapshot { run "mainExecutable" }

        objectFileFor(extraSource).assertExists()

        when:
        sourceFile << """
            // Changed source file
"""
        buildFile << """
        model {
            components {
                main {
                    binaries.all {
                        ${helloWorldApp.compilerDefine("MY_DEF")}
                    }
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
        outputs.recompiledFiles allSources
        objectFileFor(extraSource).assertDoesNotExist()
    }

    def "recompiles all source files when generated object files are removed"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        when:
        objectFileDir.deleteDir()
        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        outputs.recompiledFiles allSources
    }

    def "removes output file when source file is renamed"() {
        given:
        outputs.snapshot { run "mainExecutable" }

        when:
        final newFile = file("src/main/${app.sourceType}/changed.${app.sourceExtension}")
        newFile << sourceFile.text
        sourceFile.delete()

        and:
        run "mainExecutable"

        then:
        outputs.recompiledFile newFile
        objectFileFor(sourceFile).assertDoesNotExist()
    }

    def "removes output file when source file is removed"() {
        given:
        def extraSource = file("src/main/${app.sourceType}/extra.${app.sourceExtension}")
        extraSource << sourceFile.text.replaceAll("main", "main2")

        outputs.snapshot { run "mainExecutable" }

        and:
        objectFileFor(extraSource).assertExists()

        when:
        extraSource.delete()

        and:
        run "mainExecutable"

        then:
        executedAndNotSkipped compileTask

        and:
        objectFileFor(extraSource).assertDoesNotExist()
        outputs.noneRecompiled()
    }

    def "removes output files when all source files are removed"() {
        given:
        run "mainExecutable"

        def executable = executable("build/exe/main/main")
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
        objectFileFor(newSource).assertExists()

        and: "Previous object files are removed"
        objectFileFor(sourceFile).assertDoesNotExist()
        otherSourceFiles.each {
            objectFileFor(it).assertDoesNotExist()
        }
    }

    def "incremental compile is not effected by other compile tasks"() {
        given:
        buildFile << """
model {
    components {
        other(NativeExecutableSpec)
    }
}
"""
        app.writeSources(file("src/other"))
        app.commonHeader.writeToDir(file("src/other"))

        and:
        outputs.snapshot { run "mainExecutable" }

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
        outputs.recompiledFile sourceFile
    }

    List getAllSources() {
        return [sourceFile] + otherSourceFiles
    }
}
