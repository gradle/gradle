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
import org.gradle.plugins.cpp.model.CppExecutable
import org.gradle.plugins.cpp.model.internal.DefaultCppExecutable

import org.gradle.plugins.cpp.model.CppSourceSet
import org.gradle.plugins.cpp.model.CppLibrary

class ExecutableDsl {

    private final CppProjectExtension extension
    final String name
    Set<CppSourceSet> source = new HashSet<CppSourceSet>()
    Set<CppLibrary> libs = new HashSet<CppLibrary>()

    ExecutableDsl(CppProjectExtension extension, String name) {
        this.extension = extension
        this.name = name
    }

    ExecutableDsl source(CppSourceSet... sourceSets) {
        sourceSets.each { source.add(it) }
        this
    }

    ExecutableDsl libs(CppLibrary... libs) {
        libs.each { this.libs.add(it) }
        this
    }

    CppExecutable create() {
        def compile
        if (source) {
            compile = projectInternal.task(taskName("compile"), type: CompileCpp) {
                this.source.each { sourceSet ->
                    source sourceSet.cpp
                    headers sourceSet.headers
                }

                // This needs to be lazy somehow, libs may have includes added after the fact.
                libs.each { lib ->
                    lib.includes.each {
                        headers it
                    }
                }

                destinationDir = { "$projectInternal.buildDir/objects/${this.name}" }
            }
        }

        def link = projectInternal.task(taskName("link"), type: LinkCpp) {
            if (compile) {
                source compile.outputs.files
            }

            libs.each { lib ->
                dependsOn { lib.buildDependencies }
                source { lib.file }
            }

            output { "$project.buildDir/binaries/${this.name}" }
        }

        def executable = new DefaultCppExecutable(name, projectInternal.fileResolver)
        executable.builtBy link
        executable.file { link.output }
        executable
    }

    protected ProjectInternal getProjectInternal() {
        (ProjectInternal)extension.project
    }

    protected String taskName(verb) {
        "${verb}${name.capitalize()}Executable"
    }
}