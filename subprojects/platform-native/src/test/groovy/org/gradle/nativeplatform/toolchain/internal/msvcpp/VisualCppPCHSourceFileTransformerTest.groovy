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

import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class VisualCppPCHSourceFileTransformerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()
    def tempDir = tmpDirProvider.createDir("temp")
    def pchSourceDir = tempDir.createDir("pchGeneratedSource")
    def headerDir = tmpDirProvider.createDir("headers")
    def sourceFile = headerDir.createFile("test.h")
    VisualCppPCHSourceFileTransformer<CCompileSpec> transformer = new VisualCppPCHSourceFileTransformer<CCompileSpec>()

    def "transforms pre-compiled header spec to contain generated source files" () {
        def spec = Mock(CCompileSpec) {
            getTempDir() >> tempDir
            getSourceFiles() >> [ sourceFile ]
            isPreCompiledHeader() >> true
        }

        when:
        transformer.transform(spec)

        then:
        spec.setSourceFiles(_) >> { args ->
            def sourceFiles = args[0]
            assert sourceFiles.size() == 1
            assert sourceFiles[0].name == "test.c"
            assert sourceFiles[0].parentFile == pchSourceDir
        }
    }

    def "does not transform specs that are not pre-compiled headers" () {
        def spec = Mock(CCompileSpec) {
            isPreCompiledHeader() >> false
        }

        when:
        transformer.transform(spec)

        then:
        0 * spec.setSourceFiles(_)
    }
}
