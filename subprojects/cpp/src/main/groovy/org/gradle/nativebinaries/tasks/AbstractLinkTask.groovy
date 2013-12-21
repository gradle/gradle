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
import org.gradle.language.jvm.internal.SimpleStaleClassCleaner
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.ToolChain
import org.gradle.nativebinaries.internal.LinkerSpec

import javax.inject.Inject

@Incubating
abstract class AbstractLinkTask extends DefaultTask implements BuildBinaryTask {
    @Inject
    AbstractLinkTask() {
        libs = project.files()
        source = project.files()
    }

    /**
     * The tool chain used for linking.
     */
    ToolChain toolChain
    Platform targetPlatform

    // Invalidate output when the tool chain output changes
    @Input
    def getOutputType() {
        return "${toolChain.outputType}:${targetPlatform.compatibilityString}"
    }

    // To pick up auxiliary files produced alongside the main output file
    @OutputDirectory
    File getDestinationDir() {
        return getOutputFile().parentFile
    }

    /**
     * The file where the linked binary will be located.
     */
    @OutputFile
    File outputFile

    /**
     * Additional arguments passed to the linker.
     */
    @Input
    List<String> linkerArgs

    /**
     * The source object files to be passed to the linker.
     */
    @InputFiles
    FileCollection source

    /**
     * The library files to be passed to the linker.
     */
    @InputFiles
    FileCollection libs

    /**
     * Adds a set of object files to be linked.
     * The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void source(Object source) {
        this.source.from source
    }

    /**
     * Adds a set of library files to be linked.
     * The provided libs object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    void lib(Object libs) {
        this.libs.from libs
    }

    @TaskAction
    void link() {
        def cleaner = new SimpleStaleClassCleaner(getOutputs())
        cleaner.setDestinationDir(getDestinationDir())
        cleaner.execute()

        if (source.empty) {
            didWork = false
            return
        }

        def spec = createLinkerSpec()
        spec.tempDir = getTemporaryDir()
        spec.outputFile = getOutputFile()

        spec.objectFiles getSource()
        spec.libraries getLibs()
        spec.args getLinkerArgs()

        def result = toolChain.target(targetPlatform).createLinker().execute(spec)
        didWork = result.didWork
    }

    protected abstract LinkerSpec createLinkerSpec();
}
