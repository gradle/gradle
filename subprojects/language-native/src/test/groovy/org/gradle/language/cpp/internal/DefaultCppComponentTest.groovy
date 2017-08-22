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

class DefaultCppComponentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def component = new DefaultCppComponent(TestFiles.fileOperations(tmpDir.testDirectory), new DefaultProviderFactory())

    def "has no source files by default"() {
        expect:
        component.source.empty
        component.cppSource.empty
    }

    def "can include individual source files"() {
        def f1 = tmpDir.createFile("a.cpp")
        def f2 = tmpDir.createFile("b.c++")

        expect:
        component.source.from(f1, f2)
        component.cppSource.files == [f1, f2] as Set
    }

    def "can include source files from a directory"() {
        def d = tmpDir.createDir("dir")
        def f1 = d.createFile("a.cpp")
        def f2 = d.createFile("nested/b.cpp")
        d.createFile("ignore.txt")
        d.createFile("other/ignore.txt")

        expect:
        component.source.from(d)
        component.cppSource.files == [f1, f2] as Set
    }

    def "uses source layout convention when no other source files specified"() {
        def f1 = tmpDir.createFile("src/main/cpp/a.cpp")
        def f2 = tmpDir.createFile("src/main/cpp/nested/b.cpp")
        tmpDir.createFile("src/main/cpp/ignore-me.h")
        tmpDir.createFile("src/main/other/c.cpp")
        def f4 = tmpDir.createFile("d.cpp")

        expect:
        component.source.empty
        component.cppSource.files == [f1, f2] as Set

        component.source.from(f1, f4)
        component.cppSource.files == [f1, f4] as Set
    }

    def "does not use the convention when specified directory is empty"() {
        tmpDir.createFile("src/main/cpp/a.cpp")
        tmpDir.createFile("src/main/cpp/nested/b.cpp")
        def d = tmpDir.createDir("other")

        expect:
        component.source.from(d)
        component.cppSource.files.empty
    }

    def "uses convention for private headers when nothing specified"() {
        def d = tmpDir.file("src/main/headers")

        expect:
        component.privateHeaderDirs.files == [d] as Set
    }

    def "does not include the convention for private headers when some other location specified"() {
        def d = tmpDir.file("other")

        expect:
        component.privateHeaders.from(d)
        component.privateHeaderDirs.files == [d] as Set
    }

    def "compile include path includes private header dirs"() {
        def d = tmpDir.file("src/main/headers")
        def d2 = tmpDir.file("src/main/d1")
        def d3 = tmpDir.file("src/main/d2")
        def d4 = tmpDir.file("src/main/d3")
        def d5 = tmpDir.file("src/main/d4")

        expect:
        component.compileIncludePath.files as List == [d]

        component.privateHeaders.from(d2, d3)
        component.compileIncludePath.files as List == [d2, d3]

        component.compileIncludePath.from(d4, d5)
        component.compileIncludePath.files as List == [d2, d3, d4, d5]

        component.privateHeaders.setFrom(d3)
        component.compileIncludePath.files as List == [d3, d4, d5]
    }

    def "can query the header files of the component"() {
        def d1 = tmpDir.createDir("d1")
        def f1 = d1.createFile("a.h")
        def f2 = d1.createFile("nested/b.h")
        d1.createFile("ignore-me.cpp")
        def f3 = tmpDir.createFile("src/main/headers/c.h")
        tmpDir.createFile("src/main/headers/ignore.cpp")

        expect:
        component.headerFiles.files == [f3] as Set

        component.privateHeaders.from(d1)
        component.headerFiles.files == [f1, f2] as Set
    }
}
