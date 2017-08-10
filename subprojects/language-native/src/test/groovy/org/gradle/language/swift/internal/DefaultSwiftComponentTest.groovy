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

package org.gradle.language.swift.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultSwiftComponentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def component = new DefaultSwiftComponent(TestFiles.fileOperations(tmpDir.testDirectory))

    def "has no source files by default"() {
        expect:
        component.source.empty
        component.swiftSource.empty
    }

    def "can include individual source files"() {
        def f1 = tmpDir.createFile("a.swift")
        def f2 = tmpDir.createFile("b.swift")

        expect:
        component.source.from(f1, f2)
        component.swiftSource.files == [f1, f2] as Set
    }

    def "can include source files from a directory"() {
        def d = tmpDir.createDir("dir")
        def f1 = d.createFile("a.swift")
        def f2 = d.createFile("nested/b.swift")
        d.createFile("ignore.txt")
        d.createFile("other/ignore.txt")

        expect:
        component.source.from(d)
        component.swiftSource.files == [f1, f2] as Set
    }

    def "uses source layout convention when no other source files specified"() {
        def f1 = tmpDir.createFile("src/main/swift/a.swift")
        def f2 = tmpDir.createFile("src/main/swift/nested/b.swift")
        tmpDir.createFile("src/main/swift/ignore-me.cpp")
        tmpDir.createFile("src/main/other/c.swift")
        def f4 = tmpDir.createFile("d.swift")

        expect:
        component.source.empty
        component.swiftSource.files == [f1, f2] as Set

        component.source.from(f1, f4)
        component.swiftSource.files == [f1, f4] as Set
    }
}
