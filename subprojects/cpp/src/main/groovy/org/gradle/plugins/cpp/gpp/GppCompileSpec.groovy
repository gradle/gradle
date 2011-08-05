/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.cpp.gpp

import org.gradle.plugins.binaries.model.Binary
import org.gradle.plugins.binaries.model.Library
import org.gradle.plugins.binaries.tasks.Compile
import org.gradle.plugins.binaries.model.CompileSpec

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.file.FileCollection

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.process.internal.DefaultExecAction

import org.gradle.plugins.cpp.CppSourceSet
import org.gradle.plugins.cpp.compiler.capability.StandardCppCompiler

class GppCompileSpec implements CompileSpec, StandardCppCompiler {

    Binary binary

    final Compile task
    List<Closure> settings = []

    String outputFileName
    String baseName
    String extension

    GppCompileSpec(Binary binary) {
        this.binary = binary
        this.task = project.task("compile${name.capitalize()}", type: Compile) { spec = this }

        init()
    }

    protected init() {
        setting {
            def outputFile = getOutputFile()
            if (outputFile) {
                it.args "-o", outputFile.absolutePath
            }
        }

        task.outputs.file { getOutputFile() }

        // problem: will break if a source set is removed
        binary.sourceSets.withType(CppSourceSet).all { from(it) }
    }

    String getName() {
        binary.name
    }

    protected ProjectInternal getProject() {
        binary.project
    }

    File getWorkDir() {
        project.file "$project.buildDir/compileWork/$name"
    }

    File getOutputFile() {
        project.file "$project.buildDir/binaries/${getOutputFileName()}"
    }

    String getOutputFileName() {
        if (outputFileName) {
            outputFileName
        } else {
            extension ? "${getBaseName()}.${extension}" : getBaseName()
        }
    }

    String getBaseName() {
        baseName ?: name
    }

    void setting(Closure closure) {
        settings << closure
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
        task.inputs.files dirs
        setting {
            it.args(*dirs.srcDirs.collect { "-I${it.absolutePath}" })
        }
    }

    // special filecollection version because filecollection may be buildable
    void includes(FileCollection includeRoots) {
        task.inputs.files includeRoots
        setting {
            it.args(*includeRoots.collect { "-I${it.absolutePath}" })
        }
    }

    void includes(Iterable<File> includeRoots) {
        includeRoots.each { task.inputs.dir(it) }
        setting {
            it.args(*includeRoots.collect { "-I${it.absolutePath}" })
        }
    }

    void source(Iterable<File> files) {
        task.inputs.files files
        setting {
            it.args(*files*.absolutePath)
        }
    }

    // special filecollection version because filecollection may be buildable
    void source(FileCollection files) {
        task.inputs.files files
        setting {
            it.args(*files*.absolutePath)
        }
    }

    void libs(Iterable<Library> libs) {
        task.dependsOn { libs*.spec*.task }
        source(project.files { libs*.spec*.outputFile })
        includes(project.files { libs*.headers*.srcDirs })
    }

    void args(Object... args) {
        setting {
            it.args args
        }
    }

    void sharedLibrary() {
        setting { it.args "-shared" }
        setting { it.args "-fPIC" }

        extension = "so" // problem: this will be different on differnt platforms, need a way to “inject” this?
    }

    void compile() {
        def workDir = getWorkDir()

        ensureDirsExist(workDir, getOutputFile().parentFile)

        def compiler = new DefaultExecAction(project.fileResolver)
        compiler.executable "g++"
        compiler.workingDir workDir

        // Apply all of the settings
        settings.each { it(compiler) }

        compiler.execute()
    }

    private ensureDirsExist(File... dirs) {
        for (dir in dirs) {
            // todo: not a nice error message
            assert (dir.exists() && dir.directory) || dir.mkdirs()
        }
    }
}