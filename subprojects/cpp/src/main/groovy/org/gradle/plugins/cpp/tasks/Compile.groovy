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
package org.gradle.plugins.cpp.tasks

import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

import org.gradle.plugins.cpp.model.CompileSpec

class Compile extends DefaultTask {

    CompileSpec spec

    @TaskAction
    void compile() {
        def outputFile = spec.outputFile

        if (outputFile) {
            def parentFile = outputFile.parentFile
            assert parentFile.exists() || parentFile.mkdirs()
        }

        // question: should this just be spec.compile? i.e. do we need to know how to start the specs compile?
        spec.compiler.compile()
    }

}