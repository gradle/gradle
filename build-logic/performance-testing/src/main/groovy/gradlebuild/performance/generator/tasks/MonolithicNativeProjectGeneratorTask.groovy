/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.generator.tasks

import gradlebuild.performance.generator.TestProject
import org.gradle.work.DisableCachingByDefault

/**
 * Generates native projects with prebuilt library dependencies, using a non-standard layout
 * and with intentionally unused source files in each source set. It currently uses a single
 * component per project so we can use parallel project execution. Generates headers, C and C++
 * files.
 *
 * "monolithic" here means the project follows a monorepo layout. If we used intra-parallel
 * execution, this might be built as a single project with many components instead.
 *
 * The project also allows for "overlapping" inputs where the build output and source files are
 * arranged in a way to force the project to include the root directory as an input.
 *
 * We may also eventually include overlapping source directories, where a single directory is used
 * to build multiple components where none/some/a lot of the source files are shared between components
 * in a way that doesn't allow us to reuse the compilation steps (we don't do this now).
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
class MonolithicNativeProjectGeneratorTask extends AbstractProjectGeneratorTask {

    def generateRootProject() {
        super.generateRootProject()

        generatePrebuiltLibrarySource()
        generateCommonLibrarySource()
    }

    void generatePrebuiltLibrarySource() {
        templateArgs.prebuiltLibraries.times { prebuiltLib ->
            rootProject.sourceFiles.times { sourceIdx ->
                def fileArgs = [ sourceIdx: sourceIdx, offset: (prebuiltLib+1)*rootProject.sourceFiles ]
                generateWithTemplate(destDir, "prebuilt/lib${prebuiltLib}/include/header${sourceIdx}.h", "native-monolithic/src/prebuilt.h", fileArgs)
            }
        }
    }

    void generateCommonLibrarySource() {
        rootProject.sourceFiles.times { sourceIdx ->
            def fileArgs = [ sourceIdx: sourceIdx ]
            def destination = destDir
            if (!templateArgs.overlapWithOutput) {
                destination = new File(destDir, "common")
            }
            generateWithTemplate(destination, "common/include/header${sourceIdx}.h", "native-monolithic/src/common.h", fileArgs)
        }
    }

    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        generateProjectSource(projectDir, "h", testProject, args)
        generateProjectSource(projectDir, "c", testProject, args)
        generateProjectSource(projectDir, "cpp", testProject, args)
        projectDir.mkdirs()
    }

    void generateProjectSource(File projectDir, String sourceLang, TestProject testProject, Map args) {
        testProject.sourceFiles.times { sourceIdx ->
            def fileArgs = args + [ sourceIdx: sourceIdx, offset: (sourceIdx+1)*args.functionCount ]
            generateWithTemplate(destDir, "modules/${testProject.name}/src/src${sourceIdx}_${sourceLang}.${sourceLang}", "native-monolithic/src/src.${sourceLang}", fileArgs)
            generateWithTemplate(destDir, "modules/${testProject.name}/src/unused${sourceIdx}.${sourceLang}", "native-monolithic/src/unused.c", fileArgs)
        }
    }
}
