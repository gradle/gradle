/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.plugins.binaries.model.Library
import org.gradle.plugins.cpp.internal.CppCompileSpec

import javax.inject.Inject

class CppCompile extends DefaultTask {
    CppCompileSpec spec
    Compiler compiler

    def outputFile

    @InputFiles
    ConfigurableFileCollection libs

    @InputFiles
    ConfigurableFileCollection includes

    @InputFiles
    ConfigurableFileCollection source

    @Inject
    CppCompile() {
        libs = project.files()
        includes = project.files()
        source = project.files()
    }

    @OutputFile
    public File getOutputFile() {
        return project.file(outputFile)
    }

    @TaskAction
    void compile() {
        spec.includeRoots = includes
        spec.libs = libs
        spec.source = source
        spec.outputFile = getOutputFile()
        spec.workDir = project.file("${project.buildDir}/tmp/cppCompile/${name}")

        def result = compiler.execute(spec)
        didWork = result.didWork
    }

    void from(CppSourceSet sourceSet) {
        includes sourceSet.exportedHeaders
        source sourceSet.source
        libs sourceSet.libs

        sourceSet.nativeDependencySets.all { deps ->
            includes deps.includeRoots
            source deps.files
        }
    }

    void includes(SourceDirectorySet dirs) {
        includes.from({dirs.srcDirs})
    }

    void includes(FileCollection includeRoots) {
        includes.from(includeRoots)
    }

    void includes(Iterable<File> includeRoots) {
        includes.from(includeRoots)
    }

    void source(Iterable<File> files) {
        source.from files
    }

    void source(FileCollection files) {
        source.from files
    }

    void libs(Iterable<Library> libs) {
        dependsOn libs
        this.libs.from({ libs*.outputFile })
        includes(project.files { libs*.headers*.srcDirs })
    }
}
