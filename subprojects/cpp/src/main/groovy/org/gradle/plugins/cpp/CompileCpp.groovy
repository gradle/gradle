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
package org.gradle.plugins.cpp

import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputDirectory

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.DefaultSourceDirectorySet

import org.gradle.plugins.cpp.compiler.CppCompiler
import org.gradle.plugins.cpp.compiler.gcc.GppCppCompiler

class CompileCpp extends SourceTask {

    CppCompiler compiler

    def destinationDir

    SourceDirectorySet headers

    CompileCpp() {
        compiler = new GppCppCompiler()
        headers = new DefaultSourceDirectorySet('headers', project.fileResolver)
    }

    @OutputDirectory
    File getDestinationDir() {
        project.file(destinationDir)
    }

    SourceDirectorySet headers(Object... headerFileRoots) {
        headers.srcDirs(headerFileRoots)
    }

    @TaskAction
    void compile() {
        compiler.source = getSource()
        compiler.destinationDir = getDestinationDir()
        compiler.includes = headers.srcDirs
        compiler.execute()
    }

}