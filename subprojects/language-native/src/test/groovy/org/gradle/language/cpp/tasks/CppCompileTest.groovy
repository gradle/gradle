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

package org.gradle.language.cpp.tasks

import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

class CppCompileTest extends Specification {
    def testDir = new TestNameTestDirectoryProvider().testDirectory
    CppCompile cppCompile = TestUtil.createTask(CppCompile)
    def toolChain = Mock(NativeToolChainInternal)
    def platform = Mock(NativePlatformInternal)
    def platformToolChain = Mock(PlatformToolProvider)
    Compiler<CppCompileSpec> cppCompiler = Mock(Compiler)
    def pch = Mock(PreCompiledHeader)

    def "executes using the CppCompiler"() {
        def sourceFile = testDir.createFile("sourceFile")
        def result = Mock(WorkResult)
        when:
        cppCompile.toolChain = toolChain
        cppCompile.targetPlatform = platform
        cppCompile.compilerArgs = ["arg"]
        cppCompile.macros = [def: "value"]
        cppCompile.objectFileDir = testDir.file("outputFile")
        cppCompile.source sourceFile
        cppCompile.setPreCompiledHeader pch
        cppCompile.execute()

        then:
        _ * toolChain.outputType >> "cpp"
        platform.getArchitecture() >> Mock(ArchitectureInternal) { getName() >> "arch" }
        platform.getOperatingSystem() >> Mock(OperatingSystemInternal) { getName() >> "os" }
        1 * toolChain.select(platform) >> platformToolChain
        1 * platformToolChain.newCompiler({ CppCompileSpec.class.isAssignableFrom(it) }) >> cppCompiler
        1 * pch.includeString >> "header"
        2 * pch.prefixHeaderFile >> testDir.file("prefixHeader").createFile()
        1 * pch.objectFile >> testDir.file("pchObjectFile").createFile()
        2 * pch.pchObjects >> new SimpleFileCollection()
        1 * cppCompiler.execute({ CppCompileSpec spec ->
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
        cppCompile.didWork
    }
}
