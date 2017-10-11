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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.model.ObjectFactory
import org.gradle.language.swift.SwiftBinary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultSwiftComponentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileOperations = TestFiles.fileOperations(tmpDir.testDirectory)
    def objectFactory = TestUtil.objectFactory()
    def implementation = Stub(Configuration)
    def configurations = Stub(ConfigurationContainer)
    DefaultSwiftComponent component

    def setup() {
        _ * configurations.maybeCreate("implementation") >> implementation
        component = new TestComponent("main", fileOperations, objectFactory, configurations)
    }

    def "has an implementation configuration"() {
        expect:
        component.implementationDependencies == implementation
    }

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

    def "uses component name to determine source directory"() {
        def f1 = tmpDir.createFile("src/a/swift/a.swift")
        def f2 = tmpDir.createFile("src/b/swift/b.swift")
        def c1 = new TestComponent("a", fileOperations, objectFactory, configurations)
        def c2 = new TestComponent("b", fileOperations, objectFactory, configurations)

        expect:
        c1.swiftSource.files == [f1] as Set
        c2.swiftSource.files == [f2] as Set
    }

    class TestComponent extends DefaultSwiftComponent {
        TestComponent(String name, FileOperations fileOperations, ObjectFactory objectFactory, ConfigurationContainer configurations) {
            super(name, fileOperations, objectFactory, configurations)
        }

        @Override
        SwiftBinary getDevelopmentBinary() {
            throw new UnsupportedOperationException()
        }
    }
}
