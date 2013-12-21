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

package org.gradle.nativebinaries.toolchain.internal.gcc

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class GccToolChainTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final toolChain = new GccToolChain("gcc", OperatingSystem.current(), fileResolver, Stub(ExecActionFactory))

    def "uses shared library binary at link time"() {
        expect:
        toolChain.getSharedLibraryLinkFileName("test") == toolChain.getSharedLibraryName("test")
    }

    def "has default tool names"() {
        expect:
        toolChain.cppCompiler.executable == "g++"
        toolChain.CCompiler.executable == "gcc"
        toolChain.assembler.executable == "as"
        toolChain.linker.executable == "g++"
        toolChain.staticLibArchiver.executable == "ar"
    }

    def "can update tool names"() {
        when:
        toolChain.assembler.executable = "foo"

        then:
        toolChain.assembler.executable == "foo"
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
