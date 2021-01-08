/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.c.tasks

import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class CPreCompiledHeaderCompileTest extends AbstractProjectBuilderSpec {

    CPreCompiledHeaderCompile cPCHCompile
    def toolChain = Mock(NativeToolChainInternal)
    def platform = Mock(NativePlatformInternal)
    def platformToolChain = Mock(PlatformToolProvider)
    Compiler<CPCHCompileSpec> cPCHCompiler = Mock(Compiler)

    def setup() {
        cPCHCompile = TestUtil.createTask(CPreCompiledHeaderCompile, project)
    }

    def "executes using the C PCH Compiler"() {
        def sourceFile = temporaryFolder.createFile("sourceFile")
        def result = Mock(WorkResult)
        when:
        cPCHCompile.toolChain = toolChain
        cPCHCompile.targetPlatform = platform
        cPCHCompile.compilerArgs = ["arg"]
        cPCHCompile.macros = [def: "value"]
        cPCHCompile.objectFileDir = temporaryFolder.file("outputFile")
        cPCHCompile.source sourceFile
        execute(cPCHCompile)

        then:
        _ * toolChain.outputType >> "c"
        platform.getName() >> "testPlatform"
        platform.getArchitecture() >> Mock(ArchitectureInternal) { getName() >> "arch" }
        platform.getOperatingSystem() >> Mock(OperatingSystemInternal) { getName() >> "os" }
        1 * toolChain.select(platform) >> platformToolChain
        1 * platformToolChain.newCompiler({CPCHCompileSpec.class.isAssignableFrom(it)}) >> cPCHCompiler
        1 * cPCHCompiler.execute({ CPCHCompileSpec spec ->
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
        cPCHCompile.didWork
    }
}
