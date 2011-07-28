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

import org.gradle.plugins.cpp.model.*
import org.gradle.plugins.cpp.tasks.Compile

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.util.Configurable
import org.gradle.util.ConfigureUtil

class GppCompileSpec implements CompileSpec<Gpp>, Configurable<GppCompileSpec> {

    // likely common to all compile specs
    final String name
    final ProjectInternal project
    final NativeSourceSet sourceSet
    final Gpp compiler

    // specific to gpp
    final Compile task

    GppCompileSpec(String name, NativeSourceSet sourceSet, Gpp compiler, ProjectInternal project) {
        this.name = name
        this.sourceSet = sourceSet
        this.compiler = compiler
        this.project = project
        this.task = createTask()

        init()
    }

    // note: might only need to accept a task container instead of project
    protected Compile createTask() {
        project.task(getActionName("compile"), type: Compile) { task ->
            spec = this
        }
    }

    def workDir = { "$project.buildDir/compileWork/$uniqueName" }
    def outputFile = { "$project.buildDir/binaries/${getOutputFileName()}" }

    String outputFileName
    String baseName
    String extension

    File getWorkDir() {
        project.file(workDir)
    }

    File getOutputFile() {
        project.file(outputFile)
    }

    String getOutputFileName() {
        if (outputFileName) {
            outputFileName
        } else {
            extension ? "${getBaseName()}.${extension}" : getBaseName()
        }
    }

    String getBaseName() {
        baseName ?: uniqueName
    }
    
    String getUniqueName() {
        sourceSet.name + name.capitalize()
    }

    String getActionName(String verb) {
        verb + uniqueName.capitalize()
    }

    void includeDirSet(String sourceDirectorySetName) {
        // problem: not lazy - can't mod the source directory set after this
        includes sourceSet[sourceDirectorySetName].srcDirs.toList()
    }

    void includes(Iterable<File> files) {
        files.each { include it.absolutePath }
    }

    // todo: add as task inputs
    void includes(File... includes) {
        includes(*includes*.absolutePath)
    }

    void includes(String... includes) {
        includes.each { include(it) }
    }

    // question: do specs accept arbitrary includes? Or must they be defined in the source set?
    void include(String include) {
        args "-I$include"
    }

    void sourceDirSet(String sourceDirSetName) {
        source sourceSet[sourceDirSetName]
    }

    // todo: wire up task dependencies and inputs
    void source(FileCollection files) {
        // problem: not lazy
        args(*files*.absolutePath)
    }

    protected init() {
        args "-o", "${{->getOutputFile().absolutePath}}" // closure in gstring to keep the value lazy

        // compile position independeny, needed on rhel
        args "-fPIC"
    }

    void args(Object... args) {
        compiler.args(args)
    }

    void sharedLibrary() {
        args "-shared"
        extension = "so" // problem: this will be different on differnt platforms, need a way to “inject” this?
    }
    
    void lib(GppCompileSpec libSpec) {
        // problem: nowhere near general enough
        task.dependsOn libSpec.task

        // problem: shouldn't need this trickery for laziness
        args "${{->libSpec.getOutputFile().absolutePath}}"
    }
    
    Gpp getCompiler() {
        compiler
    }
    
    GppCompileSpec configure(Closure closure) {
        ConfigureUtil.configure(closure, this, Closure.DELEGATE_ONLY)
    }
}