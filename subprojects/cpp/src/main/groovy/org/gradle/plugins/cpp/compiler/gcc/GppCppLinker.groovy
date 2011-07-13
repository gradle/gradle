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
package org.gradle.plugins.cpp.compiler.gcc

import org.gradle.plugins.cpp.compiler.CppLinker

import org.gradle.process.internal.DefaultExecAction

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.WorkResult

class GppCppLinker implements CppLinker {

    FileCollection source
    File output

    String gpp = "g++"
    
    WorkResult execute() {
        def compilerInvocation = new DefaultExecAction().with {
            workingDir output.parentFile
            executable gpp
            args source.files*.absolutePath
            args "-o", output
        }

        def result = compilerInvocation.execute()
        result.assertNormalExitValue()
        return { true } as WorkResult
    }

}