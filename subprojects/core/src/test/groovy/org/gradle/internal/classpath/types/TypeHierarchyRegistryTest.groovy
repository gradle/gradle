/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.types

import org.gradle.api.DefaultTask
import org.gradle.internal.classpath.TypeHierarchyRegistry
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TypeHierarchyRegistryTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def "should collect all hierarchy types"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def typeRegistry = new TypeHierarchyRegistry()

        when:
        typeRegistry.visit(classD())

        then:
        def className = D.name.replace('.', '/')
        typeRegistry.getSuperTypes(className) ==~ [
            'java/lang/Comparable',
            'org/gradle/api/plugins/ExtensionAware',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$B',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$C',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$D',
            'java/lang/Object',
            'org/gradle/api/DefaultTask',
            'org/gradle/util/Configurable',
            'org/gradle/api/internal/TaskInternal',
            'org/gradle/api/internal/DynamicObjectAware',
            'org/gradle/api/internal/AbstractTask',
            'groovy/lang/GroovyObject',
            'org/gradle/api/Task'
        ]
    }

    def "should merge all type hierarchy registries"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def firstTypeRegistry = new TypeHierarchyRegistry()
        def secondTypeRegistry = new TypeHierarchyRegistry()

        when:
        firstTypeRegistry.visit(classD())
        secondTypeRegistry.visit(classF())
        def typeTypeRegistry = TypeHierarchyRegistry.of([firstTypeRegistry, secondTypeRegistry])

        then:
        typeTypeRegistry.getSuperTypes(D.name.replace('.', '/')) ==~ [
            'java/lang/Comparable',
            'org/gradle/api/plugins/ExtensionAware',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$B',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$C',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$D',
            'java/lang/Object',
            'org/gradle/api/DefaultTask',
            'org/gradle/util/Configurable',
            'org/gradle/api/internal/TaskInternal',
            'org/gradle/api/internal/DynamicObjectAware',
            'org/gradle/api/internal/AbstractTask',
            'groovy/lang/GroovyObject',
            'org/gradle/api/Task'
        ]
        typeTypeRegistry.getSuperTypes(F.name.replace('.', '/')) ==~ [
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$E',
            'org/gradle/internal/classpath/TypeHierarchyRegistryTest$F',
            'groovy/lang/GroovyObject',
            'java/lang/Object'
        ]
    }

    void classesDir(TestFile dir) {
        dir.deleteDir()
        dir.createDir()
        dir.file("D.class").bytes = classD()
        dir.file("F.class").bytes = classF()
    }

    static abstract class B extends DefaultTask {
    }
    static abstract class C extends B {
    }
    static abstract class D extends C {
    }

    static abstract class E {
    }
    static abstract class F extends E {
    }

    byte[] classD() {
        return getClass().classLoader.getResource(D.name.replace('.', '/') + ".class").bytes
    }

    byte[] classF() {
        return getClass().classLoader.getResource(F.name.replace('.', '/') + ".class").bytes
    }
}
