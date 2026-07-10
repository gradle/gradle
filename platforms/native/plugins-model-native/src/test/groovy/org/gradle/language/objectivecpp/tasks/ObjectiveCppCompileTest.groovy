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

package org.gradle.language.objectivecpp.tasks

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppCompileSpec
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class ObjectiveCppCompileTest extends AbstractProjectBuilderSpec {

    ObjectiveCppCompile objCppCompile
    def toolChain = Mock(NativeToolChainInternal)
    def platform = Mock(NativePlatformInternal)
    def platformToolChain = Mock(PlatformToolProvider)
    Compiler<ObjectiveCppCompileSpec> objCppCompiler = Mock(Compiler)
    def pch = Mock(PreCompiledHeader)

    def setup() {
        objCppCompile = TestUtil.createTask(ObjectiveCppCompile, project)
    }

    def "executes using the objCppCompiler"() {
        def sourceFile = temporaryFolder.createFile("sourceFile")
        def result = Mock(WorkResult)
        when:
        objCppCompile.toolChain = toolChain
        objCppCompile.targetPlatform = platform
        objCppCompile.compilerArgs = ["arg"]
        objCppCompile.macros = [def: "value"]
        objCppCompile.objectFileDir = temporaryFolder.file("outputFile")
        objCppCompile.source sourceFile
        objCppCompile.setPreCompiledHeader pch
        execute(objCppCompile)

        then:
        _ * toolChain.outputType >> "objcpp"
        platform.getName() >> "testPlatform"
        platform.getArchitecture() >> Mock(ArchitectureInternal) { getName() >> "arch" }
        platform.getOperatingSystem() >> Mock(OperatingSystemInternal) { getName() >> "os" }
        2 * toolChain.select(platform) >> platformToolChain
        2 * platformToolChain.newCompiler({ ObjectiveCppCompileSpec.class.isAssignableFrom(it) }) >> objCppCompiler
        pch.includeString >> "header"
        pch.prefixHeaderFile >> temporaryFolder.file("prefixHeader").createFile()
        pch.objectFile >> temporaryFolder.file("pchObjectFile").createFile()
        pch.pchObjects >> TestFiles.empty()
        1 * objCppCompiler.execute({ ObjectiveCppCompileSpec spec ->
            assert spec.sourceFiles*.name == ["sourceFile"]
            assert spec.args == ['arg']
            assert spec.allArgs == ['arg']
            assert spec.macros == [def: 'value']
            assert spec.objectFileDir.name == "outputFile"
            assert spec.preCompiledHeader == "header"
            assert spec.prefixHeaderFile.name == "prefixHeader"
            assert spec.preCompiledHeaderObjectFile.name == "pchObjectFile"
            true
        }) >> result
        1 * result.didWork >> true
        0 * _._

        and:
        objCppCompile.didWork
    }
}
