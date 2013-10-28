/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.language.c.tasks
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.api.tasks.WorkResult
import org.gradle.nativebinaries.internal.PlatformInternal
import org.gradle.nativebinaries.internal.PlatformToolChain
import org.gradle.nativebinaries.internal.ToolChainInternal
import org.gradle.nativebinaries.language.c.internal.CCompileSpec
import org.gradle.nativebinaries.language.cpp.internal.CppCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

class CCompileTest extends Specification {
    def testDir = new TestNameTestDirectoryProvider().testDirectory
    CCompile cCompile = TestUtil.createTask(CCompile)
    def toolChain = Mock(ToolChainInternal)
    def platform = Mock(PlatformInternal)
    def platformToolChain = Mock(PlatformToolChain)
    Compiler<CppCompileSpec> cCompiler = Mock(Compiler)

    def "executes using the C Compiler"() {
        def sourceFile = testDir.createFile("sourceFile")
        def result = Mock(WorkResult)
        when:
        cCompile.toolChain = toolChain
        cCompile.targetPlatform = platform
        cCompile.compilerArgs = ["arg"]
        cCompile.macros = [def: "value"]
        cCompile.objectFileDir = testDir.file("outputFile")
        cCompile.source sourceFile
        cCompile.execute()

        then:
        _ * toolChain.outputType >> "c"
        _ * platform.compatibilityString >> "p"
        1 * toolChain.target(platform) >> platformToolChain
        1 * platformToolChain.createCCompiler() >> cCompiler
        1 * cCompiler.execute({ CCompileSpec spec ->
            assert spec.sourceFiles*.name== ["sourceFile"]
            assert spec.args == ['arg']
            assert spec.allArgs == ['arg']
            assert spec.macros == [def: 'value']
            assert spec.objectFileDir.name == "outputFile"
            true
        }) >> result
        1 * result.didWork >> true
        0 * _._

        and:
        cCompile.didWork
    }
}
