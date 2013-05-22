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
import org.gradle.api.tasks.*
import org.gradle.plugins.binaries.model.Library
import org.gradle.plugins.cpp.gpp.DefaultCppCompileSpec

import javax.inject.Inject

class CppCompile extends DefaultTask {

    Compiler compiler

    @Input
    boolean sharedLibrary

    @OutputDirectory
    File outputDirectory

    @InputFiles
    ConfigurableFileCollection includes

    @InputFiles
    ConfigurableFileCollection source

    @Input
    List<Object> compilerArgs

    @Inject
    CppCompile() {
        includes = project.files()
        source = project.files()
    }

    @TaskAction
    void compile() {
        def spec = new DefaultCppCompileSpec()

        spec.workDir = getOutputDirectory() // project.file("${project.buildDir}/tmp/cppCompile/${name}")
        spec.outputFile = getOutputDirectory() // TODO:DAZ This shouldn't be required

        spec.includeRoots = includes
        spec.source = source
        spec.args = compilerArgs
        if (isSharedLibrary()) {
            spec.forDynamicLinking = true
        }

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
        includes(project.files { libs*.headers*.srcDirs })
    }
}
