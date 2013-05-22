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

package org.gradle.plugins.cpp
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.binaries.model.Library
import org.gradle.plugins.cpp.internal.LinkerSpec

import javax.inject.Inject

abstract class AbstractLinkTask extends DefaultTask {
    Compiler linker

    def outputFile

    @Input
    List<Object> linkerArgs

    @InputFiles
    ConfigurableFileCollection objectFiles

    @InputFiles
    ConfigurableFileCollection libs

    @Inject
    AbstractLinkTask() {
        libs = project.files()
        objectFiles = project.files()
    }

    @OutputFile
    public File getOutputFile() {
        return project.file(outputFile)
    }

    @TaskAction
    void link() {
        def spec = createLinkerSpec()

        spec.outputFile = getOutputFile()
        spec.workDir = project.file("${project.buildDir}/tmp/cppCompile/${name}")
        spec.objectFiles = getObjectFiles()
        spec.libs = libs

        def result = linker.execute(spec)
        didWork = result.didWork
    }

    protected abstract LinkerSpec createLinkerSpec();

    void objectFiles(FileCollection inputs) {
        objectFiles.from(inputs)
    }

    void libs(Iterable<Library> libs) {
        dependsOn libs
        this.libs.from({ libs*.outputFile })
    }
}
