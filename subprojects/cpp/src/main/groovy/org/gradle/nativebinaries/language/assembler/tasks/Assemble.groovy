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

package org.gradle.nativebinaries.language.assembler.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.ToolChain
import org.gradle.nativebinaries.language.assembler.internal.DefaultAssembleSpec

import javax.inject.Inject

/**
 * Translates Assembly language source files into object files.
 */
@Incubating
class Assemble extends DefaultTask {
    private FileCollection source

    ToolChain toolChain

    /**
     * The platform being targeted.
     */
    Platform targetPlatform

    /**
     * The directory where object files will be generated.
     */
    @OutputDirectory
    File objectFileDir

    @InputFiles @SkipWhenEmpty // Workaround for GRADLE-2026
    FileCollection getSource() {
        source
    }

    // Invalidate output when the tool chain output changes
    @Input
    def getOutputType() {
        return "${toolChain.outputType}:${targetPlatform.compatibilityString}"
    }

    /**
     * Additional arguments to provide to the assembler.
     */
    @Input
    List<String> assemblerArgs

    @Inject
    Assemble() {
        source = project.files()
    }

    @TaskAction
    void assemble() {
        def cleaner = new SimpleStaleClassCleaner(getOutputs())
        cleaner.setDestinationDir(getObjectFileDir())
        cleaner.execute()

        def spec = new DefaultAssembleSpec()
        spec.tempDir = getTemporaryDir()

        spec.objectFileDir = getObjectFileDir()
        spec.source getSource()
        spec.args getAssemblerArgs()

        def result = toolChain.target(targetPlatform).createAssembler().execute(spec)
        didWork = result.didWork
    }

    /**
     * Adds a set of assembler sources files to be translated.
     * The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void source(Object sourceFiles) {
        source.from sourceFiles
    }
}
