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
package org.gradle.plugins.cpp.dsl

import org.gradle.api.internal.project.ProjectInternal

import org.gradle.plugins.cpp.tasks.CompileCpp
import org.gradle.plugins.cpp.tasks.LinkCpp

import org.gradle.plugins.cpp.CppProjectExtension

import org.gradle.plugins.cpp.model.NativeSourceSet
import org.gradle.plugins.cpp.model.CppLibrary
import org.gradle.plugins.cpp.model.internal.DefaultCppLibrary

class LibraryDsl {

    private final CppProjectExtension extension
    final String name
    String fileExtension = "so"
    Set<NativeSourceSet> source = new HashSet<NativeSourceSet>()

    LibraryDsl(CppProjectExtension extension, String name) {
        this.extension = extension
        this.name = name
    }

    LibraryDsl source(NativeSourceSet... sourceSets) {
        sourceSets.each { source.add(it) }
        this
    }

    LibraryDsl fileExtension(String fileExtension) {
        this.fileExtension = fileExtension
        this
    }

    CppLibrary create() {
        def compile
        if (source) {
            compile = projectInternal.task(taskName("compile"), type: CompileCpp) {
                this.source.each { sourceSet ->
                    source sourceSet.cpp
                    headers sourceSet.headers
                }

                destinationDir = { "$projectInternal.buildDir/objects/${this.name}" }
            }
        }

        def link = projectInternal.task(taskName("link"), type: LinkCpp) {
            if (compile) {
                source compile.outputs.files
            }
            arg "-shared"
            output { "$project.buildDir/binaries/${this.name}.${fileExtension}" }
        }

        def library = new DefaultCppLibrary(name, projectInternal.fileResolver)
        library.builtBy link
        library.file { link.output }
        source.each { sourceSet ->
            library.include { sourceSet.headers.srcDirs }
        }
        library
    }

    protected ProjectInternal getProjectInternal() {
        (ProjectInternal)extension.project
    }
    
    protected String taskName(verb) {
        "${verb}${name.capitalize()}Library"
    }
    

}