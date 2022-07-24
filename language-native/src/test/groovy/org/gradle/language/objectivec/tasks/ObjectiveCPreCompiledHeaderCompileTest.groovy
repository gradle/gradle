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

package org.gradle.language.objectivec.tasks

import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCPCHCompileSpec
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class ObjectiveCPreCompiledHeaderCompileTest extends AbstractProjectBuilderSpec {

    ObjectiveCPreCompiledHeaderCompile objCPCHCompile
    def toolChain = Mock(NativeToolChainInternal)
    def platform = Mock(NativePlatformInternal)
    def platformToolChain = Mock(PlatformToolProvider)
    Compiler<ObjectiveCPCHCompileSpec> objCPCHCompiler = Mock(Compiler)

    def setup() {
        objCPCHCompile = TestUtil.createTask(ObjectiveCPreCompiledHeaderCompile, project)
    }

    def "executes using the C PCH Compiler"() {
        def sourceFile = temporaryFolder.createFile("sourceFile")
        def result = Mock(WorkResult)
        when:
        objCPCHCompile.toolChain = toolChain
        objCPCHCompile.targetPlatform = platform
        objCPCHCompile.compilerArgs = ["arg"]
        objCPCHCompile.macros = [def: "value"]
        objCPCHCompile.objectFileDir = temporaryFolder.file("outputFile")
        objCPCHCompile.source sourceFile
        execute(objCPCHCompile)

        then:
        _ * toolChain.outputType >> "c"
        platform.getName() >> "testPlatform"
        platform.getArchitecture() >> Mock(ArchitectureInternal) { getName() >> "arch" }
        platform.getOperatingSystem() >> Mock(OperatingSystemInternal) { getName() >> "os" }
        1 * toolChain.select(platform) >> platformToolChain
        1 * platformToolChain.newCompiler({ObjectiveCPCHCompileSpec.class.isAssignableFrom(it)}) >> objCPCHCompiler
        1 * objCPCHCompiler.execute({ ObjectiveCPCHCompileSpec spec ->
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
        objCPCHCompile.didWork
    }
}
