/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.compile

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.AntBuilderFactory
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.CompileOptions

/**
 * A dumb incremental compiler. Deletes stale classes before invoking the actual compiler
 */
class IncrementalJavaCompiler implements JavaCompiler {
    private final JavaCompiler compiler
    private final AntBuilderFactory antBuilderFactory
    private final TaskOutputsInternal taskOutputs
    private File dependencyCacheDir
    private File destinationDir
    private FileCollection source

    def IncrementalJavaCompiler(JavaCompiler compiler, AntBuilderFactory antBuilderFactory, TaskOutputsInternal taskOutputs) {
        this.compiler = compiler
        this.antBuilderFactory = antBuilderFactory
        this.taskOutputs = taskOutputs
    }

    void setSource(FileCollection source) {
        this.source = source
        compiler.setSource(source)
    }

    void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir
        compiler.setDestinationDir(destinationDir)
    }

    void setClasspath(Iterable<File> classpath) {
        compiler.setClasspath(classpath)
    }

    void setTargetCompatibility(String targetCompatibility) {
        compiler.setTargetCompatibility(targetCompatibility)
    }

    void setSourceCompatibility(String sourceCompatibility) {
        compiler.setSourceCompatibility(sourceCompatibility)
    }

    void setDependencyCacheDir(File dir) {
        dependencyCacheDir = dir
        compiler.setDependencyCacheDir(dir)
    }

    CompileOptions getCompileOptions() {
        return compiler.getCompileOptions()
    }

    WorkResult execute() {
        StaleClassCleaner cleaner
        if (compileOptions.useDepend) {
            cleaner = new AntDependsStaleClassCleaner(antBuilderFactory)
            cleaner.dependencyCacheDir = dependencyCacheDir
        } else {
            cleaner = new SimpleStaleClassCleaner(taskOutputs)
        }
        cleaner.destinationDir = destinationDir
        cleaner.source = source
        cleaner.compileOptions = compileOptions
        cleaner.execute()

        return compiler.execute()
    }
}
