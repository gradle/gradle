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

package org.gradle.nativebinaries.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.ToolChain
import org.gradle.nativebinaries.internal.DefaultStaticLibraryArchiverSpec

import javax.inject.Inject
/**
 * Assembles a static library from object files.
 */
@Incubating
class CreateStaticLibrary extends DefaultTask implements BuildBinaryTask {
    private FileCollection source

    @Inject
    CreateStaticLibrary() {
        source = project.files()
    }

    /**
     * The tool chain used for creating the static library.
     */
    ToolChain toolChain

    /**
     * The platform being targeted.
     */
    Platform targetPlatform

    // Invalidate output when the tool chain output changes
    @Input
    def getOutputType() {
        return "${toolChain.outputType}:${targetPlatform.compatibilityString}"
    }

    /**
     * The file where the output binary will be located.
     */
    @OutputFile
    File outputFile

    /**
     * The source object files to be passed to the archiver.
     */
    @InputFiles @SkipWhenEmpty // Can't use field due to GRADLE-2026
    FileCollection getSource() {
        source
    }

    /**
     * Adds a set of object files to be linked.
     * <p>
     * The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void source(Object source) {
        this.source.from source
    }

    /**
     * Additional arguments passed to the archiver.
     */
    @Input
    List<String> staticLibArgs

    @TaskAction
    void link() {
        def spec = new DefaultStaticLibraryArchiverSpec()
        spec.tempDir = getTemporaryDir()
        spec.outputFile = getOutputFile()
        spec.objectFiles getSource()
        spec.args getStaticLibArgs()

        def result = toolChain.target(targetPlatform).createStaticLibraryArchiver().execute(spec)
        didWork = result.didWork
    }

}
