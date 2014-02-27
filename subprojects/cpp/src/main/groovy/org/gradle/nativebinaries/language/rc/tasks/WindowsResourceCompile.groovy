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

package org.gradle.nativebinaries.language.rc.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.changedetection.state.FileSnapshotter
import org.gradle.api.internal.changedetection.state.TaskArtifactStateCacheAccess
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.nativebinaries.language.c.internal.incremental.IncrementalCompilerBuilder
import org.gradle.nativebinaries.language.rc.internal.DefaultWindowsResourceCompileSpec
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.ToolChain
import org.gradle.nativebinaries.toolchain.internal.PlatformToolChain

import javax.inject.Inject
/**
 * Compiles Windows Resource scripts into .res files.
 */
@Incubating
class WindowsResourceCompile extends DefaultTask {
    private final IncrementalCompilerBuilder incrementalCompilerBuilder

    @Inject
    WindowsResourceCompile() {
        incrementalCompilerBuilder = new IncrementalCompilerBuilder(services.get(TaskArtifactStateCacheAccess),
                                                                    services.get(FileSnapshotter), this)
        includes = project.files()
        source = project.files()
    }

     /**
      * The tool chain used for compilation.
      */
     ToolChain toolChain

     /**
      * The platform being targeted.
      */
     Platform targetPlatform

     /**
      * The directory where object files will be generated.
      */
     @OutputDirectory
     File outputDir

     /**
      * Returns the header directories to be used for compilation.
      */
     @InputFiles
     FileCollection includes

     /**
      * Returns the source files to be compiled.
      */
     @InputFiles
     FileCollection source

     // Invalidate output when the tool chain output changes
     @Input
     def getOutputType() {
         return "${toolChain.outputType}:${targetPlatform.compatibilityString}"
     }

     /**
      * Macros that should be defined for the compiler.
      */
     @Input
     Map<String, String> macros = [:]

     /**
      * Additional arguments to provide to the compiler.
      */
     @Input
     List<String> compilerArgs = []

     @TaskAction
     void compile(IncrementalTaskInputs inputs) {
         def spec = new DefaultWindowsResourceCompileSpec()
         spec.tempDir = getTemporaryDir()
         spec.objectFileDir = getOutputDir()
         spec.include getIncludes()
         spec.source getSource()
         spec.macros = getMacros()
         spec.args getCompilerArgs()

         PlatformToolChain platformToolChain = toolChain.target(targetPlatform)
         final compiler = platformToolChain.createWindowsResourceCompiler()
         if (!inputs.incremental) {
             incrementalCompilerBuilder.withCleanCompile()
         }
         incrementalCompilerBuilder.withIncludes(includes)
         final incrementalCompiler = incrementalCompilerBuilder.createIncrementalCompiler(compiler)
         def result = incrementalCompiler.execute(spec)
         didWork = result.didWork
     }

    /**
     * Add directories where the compiler should search for header files.
     */
    void includes(Object includeRoots) {
        includes.from(includeRoots)
    }

     /**
      * Adds a set of source files to be compiled.
      * The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
      */
     void source(Object sourceFiles) {
         source.from sourceFiles
     }
}
