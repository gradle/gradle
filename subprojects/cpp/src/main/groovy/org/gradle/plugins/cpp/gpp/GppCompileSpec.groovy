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

import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.internal.os.OperatingSystem
import org.gradle.plugins.binaries.model.NativeComponent
import org.gradle.plugins.binaries.model.CompileSpec
import org.gradle.plugins.binaries.model.Library
import org.gradle.plugins.binaries.model.internal.CompileTaskAware
import org.gradle.plugins.cpp.CppSourceSet
import org.gradle.plugins.cpp.compiler.capability.StandardCppCompiler
import org.gradle.plugins.cpp.internal.CppCompileSpec
import org.gradle.util.DeprecationLogger
import org.gradle.api.file.ConfigurableFileCollection

import org.gradle.api.tasks.TaskDependency
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.plugins.cpp.CppCompile

class GppCompileSpec implements CompileSpec, StandardCppCompiler, CompileTaskAware, CppCompileSpec {
    NativeComponent binary

    private CppCompile task
    List<Closure> settings = []

    String outputFileName
    String baseName
    String extension
    private final Compiler<? super GppCompileSpec> compiler
    private final ProjectInternal project
    private final ConfigurableFileCollection libs
    private final ConfigurableFileCollection includes
    private final ConfigurableFileCollection source

    GppCompileSpec(NativeComponent binary, Compiler<? super GppCompileSpec> compiler, ProjectInternal project) {
        this.binary = binary
        this.compiler = compiler
        this.project = project
        libs = project.files()
        includes = project.files()
        source = project.files()
    }

    void configure(CppCompile task) {
        this.task = task
        task.spec = this
        task.compiler = compiler

        task.onlyIf { !task.inputs.files.empty }
        task.outputs.file { getOutputFile() }

        // problem: will break if a source set is removed
        binary.sourceSets.withType(CppSourceSet).all { from(it) }
    }

    String getName() {
        binary.name
    }

    TaskDependency getBuildDependencies() {
        return new DefaultTaskDependency().add(task)
    }

    File getWorkDir() {
        project.file "$project.buildDir/compileWork/$name"
    }

    Iterable<File> getLibs() {
        return libs
    }

    Iterable<File> getIncludeRoots() {
        return includes
    }

    Iterable<File> getSource() {
        return source
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    String getExtension() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GppCompileSpec.getExtension()")
        return extension
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    void setExtension(String extension) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GppCompileSpec.setExtension()")
        this.extension = extension
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    void setBinary(NativeComponent binary) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GppCompileSpec.setBinary()")
        this.binary = binary
    }

    File getOutputFile() {
        project.file "$project.buildDir/binaries/${getOutputFileName()}"
    }

    String getOutputFileName() {
        if (outputFileName) {
            return outputFileName
        } else if (extension) {
            return "${getBaseName()}.${extension}"
        } else {
            return getDefaultOutputFileName()
        }
    }

    protected String getDefaultOutputFileName() {
        return OperatingSystem.current().getExecutableName(getBaseName())
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
        includes.from({dirs.srcDirs})
    }

    // special filecollection version because filecollection may be buildable
    void includes(FileCollection includeRoots) {
        task.inputs.files includeRoots
        includes.from(includeRoots)
    }

    void includes(Iterable<File> includeRoots) {
        for (File includeRoot in includeRoots) {
            task.inputs.dir(includeRoot)
        }
        includes.from(includeRoots)
    }

    void source(Iterable<File> files) {
        task.inputs.files files
        source.from files
    }

    // special filecollection version because filecollection may be buildable
    void source(FileCollection files) {
        task.inputs.source files
        source.from files
    }

    void libs(Iterable<Library> libs) {
        task.dependsOn libs
        this.libs.from({ libs*.spec*.outputFile })
        includes(project.files { libs*.headers*.srcDirs })
    }

    void args(Object... args) {
        setting {
            it.args args
        }
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    void sharedLibrary() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("CompileSpec.sharedLibrary()")
    }

    /**
     * @deprecated No replacement
     */
    @Deprecated
    void compile() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("CompileSpec.compile()")
        compiler.execute(this)
    }
}