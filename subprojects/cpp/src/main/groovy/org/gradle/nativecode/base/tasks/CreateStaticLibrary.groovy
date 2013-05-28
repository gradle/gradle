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

@Incubating
class CreateStaticLibrary extends DefaultTask {
    ToolChainInternal toolChain

    @OutputFile
    File outputFile

    @InputFiles
    FileCollection source

    @SkipWhenEmpty // Workaround for GRADLE-2026
    FileCollection getSource() {
        source
    }

    @Inject
    CreateStaticLibrary() {
        source = project.files()
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

    void source(Object inputs) {
        source.from inputs
    }

    void lib(Object libs) {
        this.libs.from libs
    }
}
