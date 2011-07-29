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

import org.gradle.plugins.nativ.model.*
import org.gradle.plugins.nativ.tasks.Compile

import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.process.internal.DefaultExecAction

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
        setting {
            it.args "-fPIC"
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

    void includes(SourceDirectorySet dirs) {
        task.inputs.files dirs
        setting {
            it.args(*dirs.srcDirs.collect { "-I${it.absolutePath}" })
        }
    }

    void includes(FileCollection files) {
        task.inputs.files files
        includes((Iterable<File>)files)
    }

    void includes(Iterable<File> files) {
        setting {
            it.args(*files.collect { "-I${it.absolutePath}" })
        }
    }

    void source(FileCollection files) {
        task.inputs.files files
        source((Iterable<File>)files)
    }

    void source(Iterable<File> files) {
        setting {
            it.args(*files*.absolutePath)
        }
    }

    void source(CompileSpec spec) {
        task.dependsOn spec.task
        source { spec.outputFile }
    }

    void source(Closure source) {
        task.inputs.file source
        setting { it.args source() }
    }

    void args(Object... args) {
        setting {
            it.args args
        }
    }

    void sharedLibrary() {
        setting { it.args "-shared" }
        extension = "so" // problem: this will be different on differnt platforms, need a way to “inject” this?
    }

    void compile() {
        def workDir = getWorkDir()
        assert (workDir.exists() && workDir.directory) || workDir.mkdirs()

        def outputFile = getOutputFile()
        def outputFileDir = outputFile.parentFile
        assert outputFileDir.exists() || outputFileDir.mkdirs()

        def compiler = new DefaultExecAction(project.fileResolver)
        compiler.executable "g++"
        compiler.workingDir getWorkDir()

        // Apply all of the settings
        settings.each { it(compiler) }

        compiler.execute()
    }
}