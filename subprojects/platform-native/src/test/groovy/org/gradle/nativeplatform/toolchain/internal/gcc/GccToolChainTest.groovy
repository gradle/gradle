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

package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProviderFactory
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class GccToolChainTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    Instantiator instantiator = DirectInstantiator.INSTANCE

    final toolChain = new GccToolChain(instantiator , "gcc", Stub(BuildOperationExecutor), OperatingSystem.current(), fileResolver, Stub(ExecActionFactory), Stub(CompilerOutputFileNamingSchemeFactory), Stub(CompilerMetaDataProviderFactory), Stub(WorkerLeaseService))

    def "provides default tools"() {
        def action = Mock(Action)

        when:
        toolChain.target("platform", action)
        toolChain.select(Stub(NativePlatformInternal) { getName() >> "platform" })

        then:
        1 * action.execute(_) >> { GccPlatformToolChain platformToolChain ->
            assert platformToolChain.assembler.executable == 'gcc'
            assert platformToolChain.cCompiler.executable == 'gcc'
            assert platformToolChain.cppCompiler.executable == 'g++'
            assert platformToolChain.objcCompiler.executable == 'gcc'
            assert platformToolChain.objcppCompiler.executable == 'g++'
            assert platformToolChain.linker.executable == 'g++'
            assert platformToolChain.staticLibArchiver.executable == 'ar'
        }
    }

    def "resolves path entries"() {
        def testDir = tmpDirProvider.testDirectory

        when:
        toolChain.path "The Path"
        toolChain.path "Path1", "Path2"

        then:
        fileResolver.resolve("The Path") >> testDir.file("one")
        fileResolver.resolve("Path1") >> testDir.file("two")
        fileResolver.resolve("Path2") >> testDir.file("three")

        and:
        toolChain.path == [testDir.file("one"), testDir.file("two"), testDir.file("three")]
    }
}
