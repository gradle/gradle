/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification


class DefaultCppLibraryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def library = new DefaultCppLibrary(TestFiles.fileOperations(tmpDir.testDirectory), new DefaultProviderFactory())

    def "uses convention for public headers when nothing specified"() {
        def d = tmpDir.file("src/main/public")

        expect:
        library.publicHeaderDirs.files == [d] as Set
    }

    def "does not include the convention for public headers when some other location specified"() {
        def d = tmpDir.file("other")

        expect:
        library.publicHeaders.from(d)
        library.publicHeaderDirs.files == [d] as Set
    }

    def "compile include path includes public and private header dirs"() {
        def defaultPrivate = tmpDir.file("src/main/headers")
        def defaultPublic = tmpDir.file("src/main/public")
        def d1 = tmpDir.file("src/main/d1")
        def d2 = tmpDir.file("src/main/d2")
        def d3 = tmpDir.file("src/main/d3")
        def d4 = tmpDir.file("src/main/d4")

        expect:
        library.compileIncludePath.files as List == [defaultPublic, defaultPrivate]

        library.publicHeaders.from(d1)
        library.privateHeaders.from(d2)
        library.compileIncludePath.files as List == [d1, d2]

        library.compileIncludePath.from(d3, d4)
        library.compileIncludePath.files as List == [d1, d2, d3, d4]

        library.publicHeaders.setFrom(d3)
        library.compileIncludePath.files as List == [d3, d2, d4]
    }

    def "can query the header files of the library"() {
        def d1 = tmpDir.createDir("d1")
        def f1 = d1.createFile("a.h")
        def f2 = d1.createFile("nested/b.h")
        d1.createFile("ignore-me.cpp")
        def f3 = tmpDir.createFile("src/main/public/c.h")
        def f4 = tmpDir.createFile("src/main/headers/c.h")
        tmpDir.createFile("src/main/headers/ignore.cpp")
        tmpDir.createFile("src/main/public/ignore.cpp")

        expect:
        library.headerFiles.files == [f3, f4] as Set

        library.privateHeaders.from(d1)
        library.headerFiles.files == [f3, f1, f2] as Set
    }
}
