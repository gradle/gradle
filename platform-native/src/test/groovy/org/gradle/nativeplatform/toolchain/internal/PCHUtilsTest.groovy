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

package org.gradle.nativeplatform.toolchain.internal

import com.google.common.collect.Lists
import org.gradle.api.Transformer
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.Specification

class PCHUtilsTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider(getClass())

    def "generates a prefix header file" () {
        def headers = Lists.newArrayList()
        headers.add "header.h"
        headers.add "<stdio.h>"
        headers.add "some/path/to/another.h"
        def tempDir = tmpDirProvider.createDir("temp")
        def prefixHeaderFile = new File(tempDir, "prefix-headers.h")

        when:
        PCHUtils.generatePrefixHeaderFile(headers, prefixHeaderFile)

        then:
        prefixHeaderFile.text == TextUtil.toPlatformLineSeparators(
"""#include "header.h"
#include <stdio.h>
#include "some/path/to/another.h"
""")
    }

    def "can generate a source file for a pre-compiled header" () {
        given:
        def tempDir = tmpDirProvider.createDir("temp")
        def pchSourceDir = tempDir.createDir("pchGenerated")
        def headerDir = tmpDirProvider.createDir("headers")
        def sourceFile = headerDir.createFile("test.h")
        def spec = Mock(type) {
            getTempDir() >> tempDir
        }

        when:
        def generated = PCHUtils.generatePCHSourceFile(spec, sourceFile)

        then:
        generated.name == "test.${extension}"
        generated.parentFile == pchSourceDir
        generated.text == "#include \"test.h\""
        pchSourceDir.assertContainsDescendants("test.h", "test.${extension}")

        where:
        type              | extension
        CPCHCompileSpec   | "c"
        CppPCHCompileSpec | "cpp"
    }

    def "generates a PCH object directory" () {
        given:
        def tempDir = tmpDirProvider.createDir("temp")
        def objectDir = tmpDirProvider.createDir("pch")
        def prefixFile = tempDir.createFile("header.h")
        def objectFile = tempDir.createFile("header.o")
        prefixFile << "#include <stdio.h>"
        objectFile << "some content"

        when:
        def generated = PCHUtils.generatePCHObjectDirectory(objectDir, prefixFile, objectFile)

        then:
        generated.parentFile == objectDir
        generated.name == "preCompiledHeaders"
        generated.listFiles().collect { it.name }.sort() == [ 'header.h', 'header.o' ]
        new File(generated, "header.h").bytes == prefixFile.bytes
        new File(generated, "header.o").bytes == objectFile.bytes
    }

    def "transforms pre-compiled header spec to contain generated source files" () {
        given:
        def tempDir = tmpDirProvider.createDir("temp")
        def pchSourceDir = tempDir.createDir("pchGenerated")
        def headerDir = tmpDirProvider.createDir("headers")
        def sourceFile = headerDir.createFile("test.h")
        Transformer<CPCHCompileSpec, CPCHCompileSpec> transformer = PCHUtils.getHeaderToSourceFileTransformer(CPCHCompileSpec)
        def spec = Mock(CPCHCompileSpec) {
            getTempDir() >> tempDir
            getSourceFiles() >> [ sourceFile ]
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
}
