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

package org.gradle.nativebinaries.language.c.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.*
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.ToolChain
import org.gradle.nativebinaries.internal.PlatformToolChain
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec

import javax.inject.Inject
/**
 * Compiles native source files into object files.
 */
@Incubating
abstract class AbstractNativeCompileTask extends DefaultTask {
    private FileCollection source

    /**
     * The tool chain used for compilation.
     */
    ToolChain toolChain

    /**
     * The platform being targeted.
     */
    // TODO:DAZ This should form an @Input
    Platform targetPlatform

    /**
     * Should the compiler generate position independent code?
     */
    @Input
    boolean positionIndependentCode

    /**
     * The directory where object files will be generated.
     */
    @OutputDirectory
    File objectFileDir

    /**
     * Returns the header directories to be used for compilation.
     */
    @InputFiles
    FileCollection includes

    /**
     * Returns the source files to be compiled.
     */
    @InputFiles @SkipWhenEmpty // Workaround for GRADLE-2026
    FileCollection getSource() {
        source
    }

    // TODO:DAZ We also need the platform to form an input
    // Invalidate output when the tool chain output changes
    @Input
    def getOutputType() {
        return toolChain.outputType
    }

    /**
     * Macros that should be defined for the compiler.
     */
    @Input
    Map<String, String> macros

    /**
     * Additional arguments to provide to the compiler.
     */
    @Input
    List<String> compilerArgs

    @Inject
    AbstractNativeCompileTask() {
        includes = project.files()
        source = project.files()
    }

    @TaskAction
    void compile() {
        def cleaner = new SimpleStaleClassCleaner(getOutputs())
        cleaner.setDestinationDir(getObjectFileDir())
        cleaner.execute()

        def spec = createCompileSpec()
        spec.tempDir = getTemporaryDir()

        spec.objectFileDir = getObjectFileDir()
        spec.include getIncludes()
        spec.source getSource()
        spec.macros = getMacros()
        spec.args getCompilerArgs()
        spec.positionIndependentCode = isPositionIndependentCode()

        PlatformToolChain platformToolChain = toolChain.target(targetPlatform)
        def result = execute(platformToolChain, spec)
        didWork = result.didWork
    }

    protected abstract NativeCompileSpec createCompileSpec();

    protected abstract WorkResult execute(PlatformToolChain toolChain, NativeCompileSpec spec);

    /**
     * Add locations where the compiler should search for header files.
     */
    void includes(SourceDirectorySet dirs) {
        includes.from({dirs.srcDirs})
    }

    /**
     * Add directories where the compiler should search for header files.
     */
    void includes(FileCollection includeRoots) {
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
