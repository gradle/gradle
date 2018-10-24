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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.DisplayName
import org.gradle.language.ComponentDependencies
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultCppComponentTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileOperations = TestFiles.fileOperations(tmpDir.testDirectory)
    def objectFactory = TestUtil.objectFactory()
    def targetMachineFactory = new DefaultTargetMachineFactory(objectFactory)
    DefaultCppComponent component

    def setup() {
        component = new TestComponent("main", fileOperations, objectFactory, targetMachineFactory)
    }

    def "has no source files by default"() {
        expect:
        component.source.empty
        component.cppSource.empty
    }

    def "can include individual source files"() {
        def f1 = tmpDir.createFile("a.cpp")
        def f2 = tmpDir.createFile("b.c++")
        def f3 = tmpDir.createFile("c.cc")

        expect:
        component.source.from(f1, f2, f3)
        component.cppSource.files == [f1, f2, f3] as Set
    }

    def "can include source files from a directory"() {
        def d = tmpDir.createDir("dir")
        def f1 = d.createFile("a.cpp")
        def f2 = d.createFile("nested/b.cpp")
        def f3 = d.createFile("c.cc")
        d.createFile("ignore.txt")
        d.createFile("other/ignore.txt")

        expect:
        component.source.from(d)
        component.cppSource.files == [f1, f2, f3] as Set
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

    def "uses component name to determine source directories"() {
        def f1 = tmpDir.createFile("src/a/cpp/a.cpp")
        def h1 = tmpDir.createFile("src/a/headers")
        def f2 = tmpDir.createFile("src/b/cpp/b.cpp")
        def h2 = tmpDir.createFile("src/b/headers")
        def c1 = new TestComponent("a", fileOperations, objectFactory, targetMachineFactory)
        def c2 = new TestComponent("b", fileOperations, objectFactory, targetMachineFactory)

        expect:
        c1.cppSource.files == [f1] as Set
        c1.privateHeaderDirs.files == [h1] as Set
        c2.cppSource.files == [f2] as Set
        c2.privateHeaderDirs.files == [h2] as Set
    }

    static class TestComponent extends DefaultCppComponent {
        TestComponent(String name, FileOperations fileOperations, ObjectFactory objectFactory, TargetMachineFactory targetMachineFactory) {
            super(name, fileOperations, objectFactory, targetMachineFactory)
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
