/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import org.gradle.play.internal.coffeescript.CoffeeScriptCompileSpec
import org.gradle.util.TestUtil
import spock.lang.Specification
import org.gradle.language.base.internal.compile.Compiler

/**
 *
 */
class CoffeeScriptCompileTest extends Specification {
    DefaultProject project = TestUtil.createRootProject()
    CoffeeScriptCompile compileTask = project.tasks.create("coffeeScriptCompile", CoffeeScriptCompile);
    Compiler<CoffeeScriptCompileSpec> compiler = Mock(Compiler)
    IncrementalTaskInputs taskInputs = Mock(IncrementalTaskInputs)

    def setup() {
        compileTask.compiler = compiler
    }

    def "invokes coffeescript compiler" () {
        compileTask.outputDirectory = Mock(File)

        when:
        compileTask.compile(taskInputs)

        then:
        1 * compiler.execute(_)
    }
}
