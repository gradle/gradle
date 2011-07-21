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
import org.gradle.api.tasks.OutputFile

import org.gradle.plugins.cpp.compiler.CppLinker
import org.gradle.plugins.cpp.compiler.gcc.GppCppLinker

class LinkCpp extends SourceTask {

    CppLinker linker = new GppCppLinker()

    def output

    List<String> args = []
    
    @OutputFile
    File getOutput() {
        project.file(output)
    }

    LinkCpp output(output) {
        this.output = output
        this
    }
    
    @TaskAction
    void link() {
        linker.source = getSource()
        linker.output = getOutput()
        linker.args = args.toArray()
        linker.execute()
    }
    
    LinkCpp arg(String arg) {
        args(arg)
    }
    
    LinkCpp args(String... args) {
        args.each { this.args.add(it) }
        this
    }
}