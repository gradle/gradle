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

package org.gradle.api.tasks.javadoc

import org.apache.commons.io.FileUtils
import org.gradle.api.internal.file.TestFiles
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.platform.base.internal.toolchain.ToolProvider
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class JavadocTest extends AbstractProjectBuilderSpec {
    def testDir = temporaryFolder.getTestDirectory()
    def destDir = new File(testDir, "dest")
    def srcDir = new File(testDir, "srcdir")
    def toolChain = Mock(JavaToolChainInternal)
    def toolProvider = Mock(ToolProvider)
    def generator = Mock(Compiler)
    def configurationMock = TestFiles.fixed(new File("classpath"))
    def executable = "somepath"
    Javadoc task

    def setup() {
        task = TestUtil.createTask(Javadoc, project)
        task.setClasspath(configurationMock)
        task.setExecutable(executable)
        task.setToolChain(toolChain)
        FileUtils.touch(new File(srcDir, "file.java"))
    }

    def defaultExecution() {
        when:
        task.setDestinationDir(destDir)
        task.source(srcDir)

        and:
        execute(task)

        then:
        1 * toolChain.select(_) >> toolProvider
        1 * toolProvider.newCompiler(!null) >> generator
        1 * generator.execute(_)
    }

    def executionWithOptionalAttributes() {
        when:
        task.setDestinationDir(destDir)
        task.source(srcDir)
        task.setMaxMemory("max-memory")
        task.setVerbose(true)

        and:
        execute(task)

        then:
        1 * toolChain.select(_) >> toolProvider
        1 * toolProvider.newCompiler(!null) >> generator
        1 * generator.execute(_)
    }
}
