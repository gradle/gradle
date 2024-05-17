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
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.DisplayName
import org.gradle.language.ComponentDependencies
import org.gradle.language.swift.SwiftBinary
import org.gradle.language.swift.SwiftVersion
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import javax.inject.Inject

@UsesNativeServices
class DefaultSwiftComponentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(tmpDir.testDirectory)
    DefaultSwiftComponent component

    def setup() {
        component = project.objects.newInstance(TestComponent, "main")
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
        def c1 = project.objects.newInstance(TestComponent, "a")
        def c2 = project.objects.newInstance(TestComponent, "b")

        expect:
        c1.swiftSource.files == [f1] as Set
        c2.swiftSource.files == [f2] as Set
    }

    def "can modify Swift source compatibility"() {
        component.sourceCompatibility.set SwiftVersion.SWIFT4

        expect:
        component.sourceCompatibility.get() == SwiftVersion.SWIFT4
    }

    def "defaults to null when Swift source compatibility isn't configured"() {
        expect:
        component.sourceCompatibility.getOrNull() == null
    }

    static class TestComponent extends DefaultSwiftComponent<SwiftBinary> {
        @Inject
        TestComponent(String name, ObjectFactory objectFactory) {
            super(name, objectFactory)
        }

        @Override
        DisplayName getDisplayName() {
            throw new UnsupportedOperationException()
        }

        @Override
        Configuration getImplementationDependencies() {
            throw new UnsupportedOperationException()
        }

        @Override
        ComponentDependencies getDependencies() {
            throw new UnsupportedOperationException()
        }
    }
}
