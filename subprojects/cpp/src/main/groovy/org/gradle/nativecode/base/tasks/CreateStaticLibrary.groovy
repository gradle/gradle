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

package org.gradle.nativecode.base.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.nativecode.base.internal.ToolChainInternal
import org.gradle.nativecode.base.internal.StaticLibraryArchiverSpec

import javax.inject.Inject

/**
 * Assembles a static library from object files.
 */
@Incubating
class CreateStaticLibrary extends DefaultTask {
    private FileCollection source

    @Inject
    CreateStaticLibrary() {
        source = project.files()
    }

    /**
     * The tool chain used for creating the static library.
     */
    ToolChainInternal toolChain

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

    @TaskAction
    void link() {
        def spec = new Spec()
        spec.tempDir = getTemporaryDir()

        spec.outputFile = getOutputFile()
        spec.source = getSource()

        def result = toolChain.createStaticLibraryArchiver().execute(spec)
        didWork = result.didWork
    }

    private static class Spec implements StaticLibraryArchiverSpec {
        Iterable<File> source;
        File outputFile;
        File tempDir;
    }
}
