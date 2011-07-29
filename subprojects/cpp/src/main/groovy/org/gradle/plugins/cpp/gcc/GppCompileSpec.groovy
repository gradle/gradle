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
package org.gradle.plugins.cpp.gcc

import org.gradle.plugins.binaries.tasks.Compile
import org.gradle.plugins.binaries.model.CompileSpec

import org.gradle.api.file.SourceDirectorySet

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.process.internal.DefaultExecAction

import org.gradle.plugins.cpp.CppSourceSet

class GppCompileSpec implements CompileSpec {

    // likely common to all compile specs
    final String name
    final ProjectInternal project

    final Compile task
    List<Closure> settings = []

    String outputFileName
    String baseName
    String extension

    GppCompileSpec(String name, ProjectInternal project) {
        this.name = name
        this.project = project
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
        includes sourceSet.headers
        source sourceSet.source
    }

    void includes(SourceDirectorySet dirs) {
        task.inputs.files dirs
        setting {
            it.args(*dirs.srcDirs.collect { "-I${it.absolutePath}" })
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

    // problem: hack for now, to support linking against libs
    void source(GppCompileSpec spec) {
        task.dependsOn spec.task
        source project.files { spec.outputFile }
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