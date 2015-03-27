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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class VisualCppPCHSourceFileGeneratorUtilTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def "can generate a source file for a pre-compiled header" () {
        given:
        def tempDir = tmpDirProvider.createDir("temp")
        def pchSourceDir = tempDir.createDir("pchGeneratedSource")
        def headerDir = tmpDirProvider.createDir("headers")
        def sourceFile = headerDir.createFile("test.h")
        def spec = Mock(type) {
            getTempDir() >> tempDir
        }

        when:
        def generated = VisualCppPCHSourceFileGeneratorUtil.generatePCHSourceFile(spec, sourceFile)

        then:
        generated.name == "test.${extension}"
        generated.parentFile == pchSourceDir
        generated.text == "#include \"test.h\""
        pchSourceDir.assertContainsDescendants("test.h", "test.${extension}")

        where:
        type           | extension
        CPCHCompileSpec   | "c"
        CppPCHCompileSpec | "cpp"
    }
}
