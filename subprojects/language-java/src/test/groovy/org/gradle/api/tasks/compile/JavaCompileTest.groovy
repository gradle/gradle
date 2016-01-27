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

package org.gradle.api.tasks.compile

import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.tasks.WorkResult
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.platform.base.internal.toolchain.ToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class JavaCompileTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def toolChain = Mock(JavaToolChainInternal)
    def platform = Mock(JavaPlatform)
    def toolProvider = Mock(ToolProvider)
    def compiler = Mock(Compiler)
    def task = TestUtil.createTask(JavaCompile)

    def "uses specified ToolChain to create a Compiler to do the work"() {
        given:
        task.outputs.history = Stub(TaskExecutionHistory)
        task.destinationDir = tmpDir.file("classes")
        task.toolChain = toolChain

        when:
        task.compile()

        then:
        1 * toolChain.select(_) >> toolProvider
        1 * toolProvider.newCompiler(!null) >> compiler
        1 * compiler.execute(!null) >> Stub(WorkResult)
    }
}
