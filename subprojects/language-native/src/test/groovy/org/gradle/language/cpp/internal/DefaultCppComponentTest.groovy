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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultCppComponentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def component = new DefaultCppComponent(TestFiles.fileOperations(tmpDir.testDirectory))

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
}
