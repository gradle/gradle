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

package org.gradle.nativeplatform.toolchain.internal.gcc

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.clang.ClangToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class ClangToolChainTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider(getClass())
    final FileResolver fileResolver = Mock(FileResolver)
    final Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    final toolChain = new ClangToolChain("clang", Stub(BuildOperationExecutor), Stub(OperatingSystem), fileResolver, Stub(ExecActionFactory), Stub(CompilerOutputFileNamingSchemeFactory), Stub(CompilerMetaDataProviderFactory), Stub(SystemLibraryDiscovery), instantiator, Stub(WorkerLeaseService))

    def "provides default tools"() {
        def action = Mock(Action)

        when:
        toolChain.target("platform", action)
        toolChain.select(Stub(NativePlatformInternal) { getName() >> "platform" })

        then:
        1 * action.execute(_) >> { GccPlatformToolChain platformToolChain ->
            assert platformToolChain.assembler.executable == 'clang'
            assert platformToolChain.cCompiler.executable == 'clang'
            assert platformToolChain.cppCompiler.executable == 'clang++'
            assert platformToolChain.objcCompiler.executable == 'clang'
            assert platformToolChain.objcppCompiler.executable == 'clang++'
            assert platformToolChain.linker.executable == 'clang++'
            assert platformToolChain.staticLibArchiver.executable == 'ar'
        }
    }
}
