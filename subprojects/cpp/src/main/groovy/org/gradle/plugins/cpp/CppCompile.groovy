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
import org.gradle.api.Incubating
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.*
import org.gradle.plugins.cpp.internal.CppCompileSpec
import org.gradle.plugins.cpp.internal.DefaultCppCompileSpec

import javax.inject.Inject

@Incubating
class CppCompile extends DefaultTask {
    private FileCollection source

    def toolChain

    @Input
    boolean forDynamicLinking

    @OutputDirectory
    File objectFileDir

    @InputFiles
    FileCollection includes

    @InputFiles @SkipWhenEmpty // Workaround for GRADLE-2026
    FileCollection getSource() {
        source
    }

    @Input
    List<String> compilerArgs

    @Inject
    CppCompile() {
        includes = project.files()
        source = project.files()
    }

    @TaskAction
    void compile() {
        def spec = new DefaultCppCompileSpec()
        spec.tempDir = getTemporaryDir()

        spec.objectFileDir = getObjectFileDir()
        spec.includeRoots = getIncludes()
        spec.source = getSource()
        spec.args = getCompilerArgs()
        if (isForDynamicLinking()) {
            spec.forDynamicLinking = true
        }

        def result = toolChain.createCompiler(CppCompileSpec).execute(spec)
        didWork = result.didWork
    }

    void includes(SourceDirectorySet dirs) {
        includes.from({dirs.srcDirs})
    }

    void includes(FileCollection includeRoots) {
        includes.from(includeRoots)
    }

    void source(Object sourceFiles) {
        source.from sourceFiles
    }
}
