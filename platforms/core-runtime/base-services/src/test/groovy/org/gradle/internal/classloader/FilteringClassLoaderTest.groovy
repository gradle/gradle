/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.classloader

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Before
import org.junit.Test
import org.junit.runners.BlockJUnit4ClassRunner
import spock.lang.Issue
import spock.lang.Specification

import static org.junit.Assert.fail

class FilteringClassLoaderTest extends Specification {
    private FilteringClassLoader classLoader

    def setup() {
        withSpec {}
    }

    void passesThroughSystemClasses() {
        expect:
        canLoadClass(String)
    }

    void passesThroughSystemPackages() {
        expect:
        canSeePackage('java.lang')
    }

    @Issue("gradle/core-issues#115")
    void passesThroughSystemResources() {
        expect:
        canSeeResource('java/lang/Object.class')
    }

    void filtersClassesByDefault() {
        given:
        classLoader.parent.loadClass(Test.class.name)

        when:
        classLoader.loadClass(Test.class.name, false)

        then:
        ClassNotFoundException e = thrown()
        e.message == "$Test.name not found."

        when:
        classLoader.loadClass(Test.class.name)

        then:
        ClassNotFoundException e2 = thrown()
        e2.message == "$Test.name not found."
    }

    void filtersPackagesByDefault() {
        given:
        assert classLoader.parent.getPackage('org.junit') != null

        expect:
        cannotSeePackage('org.junit')
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    void filtersPackagesByDefaultPostJdk8() {
        given:
        assert classLoader.parent.getDefinedPackage('org.junit') != null

        expect:
        cannotSeePackage('org.junit')
    }

    void filtersResourcesByDefault() {
        given:
        assert classLoader.parent.getResource('org/gradle/util/ClassLoaderTest.txt') != null

        expect:
        cannotSeeResource('org/gradle/util/ClassLoaderTest.txt')
    }

    void passesThroughClassesInSpecifiedPackagesAndSubPackages() {
        given:
        cannotLoadClass(Test)
        cannotLoadClass(BlockJUnit4ClassRunner)

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowPackage('org.junit')
        }

        expect:
        canLoadClass(Test)
        canLoadClass(Before)
        canLoadClass(BlockJUnit4ClassRunner)
    }

    void passesThroughSpecifiedClasses() {
        given:
        cannotLoadClass(Test)

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowClass(Test.class)
        }

        expect:
        canLoadClass(Test)
        cannotLoadClass(Before)
    }

    void filtersSpecifiedClasses() {
        given:
        cannotLoadClass(Test)
        cannotLoadClass(Before)

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowPackage("org.junit")
            spec.disallowClass("org.junit.Test")
        }

        expect:
        canLoadClass(Before)
        cannotLoadClass(Test)
    }

    void disallowClassWinsOverAllowClass() {
        given:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowClass(Test)
            spec.disallowClass(Test.name)
        }

        expect:
        cannotLoadClass(Test)
    }

    void passesThroughSpecifiedPackagesAndSubPackages() {
        given:
        cannotSeePackage('org.junit')
        cannotSeePackage('org.junit.runner')

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowPackage('org.junit')
        }

        expect:
        canSeePackage('org.junit')
        canSeePackage('org.junit.runner')
    }

    void passesThroughDefaultPackage() {
        given:
        cannotLoadClass(ClassInDefaultPackage)

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowPackage(FilteringClassLoader.DEFAULT_PACKAGE)
        }

        expect:
        canLoadClass(ClassInDefaultPackage)
    }

    void passesThroughResourcesInSpecifiedPackages() {
        given:
        cannotSeeResource('org/gradle/util/ClassLoaderTest.txt')

        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowPackage('org.gradle')
        }

        expect:
        canSeeResource('org/gradle/util/ClassLoaderTest.txt')
    }

    void passesThroughResourcesWithSpecifiedPrefix() {
        given:
        cannotSeeResource('org/gradle/util/ClassLoaderTest.txt')

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowResources('org/gradle')
        }

        expect:
        canSeeResource('org/gradle/util/ClassLoaderTest.txt')
    }

    void passesThroughSpecifiedResources() {
        given:
        cannotSeeResource('org/gradle/util/ClassLoaderTest.txt')

        and:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowResource('org/gradle/util/ClassLoaderTest.txt')
        }

        expect:
        canSeeResource('org/gradle/util/ClassLoaderTest.txt')
    }

    void "can disallow packages"() {
        given:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.disallowPackage("org.junit")
        }

        expect:
        cannotLoadClass(Test)
        cannotSeePackage("org.junit")
        cannotSeePackage("org.junit.subpackage")
    }

    void "disallow wins over allow packages"() {
        given:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.disallowPackage("org.junit")
            spec.allowPackage("org.junit")
        }

        expect:
        cannotLoadClass(Test)
    }

    void "allow class wins over disallow package"() {
        given:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.disallowPackage("org.junit")
            spec.allowClass(Test)
        }

        expect:
        canLoadClass(Test)
        cannotLoadClass(Before)
    }

    void "visits self and parent"() {
        def visitor = Mock(ClassLoaderVisitor)
        given:
        withSpec { FilteringClassLoader.Spec spec ->
            spec.allowClass(Test)
            spec.allowPackage("org.junit")
            spec.allowResource("a/b/c")
            spec.disallowClass(Before.name)
        }

        when:
        classLoader.visit(visitor)

        then:
        1 * visitor.visitSpec({ it instanceof FilteringClassLoader.Spec }) >> { FilteringClassLoader.Spec spec ->
            spec.classNames == [Test.name]
            spec.disallowedClassNames == [Before.name]
            spec.packageNames == ["org.junit"]
            spec.packagePrefixes == ["org.junit."]
            spec.resourceNames == ["a/b/c"]
            spec.resourcePrefixes == ["org/junit/"]
        }
        1 * visitor.visitParent(classLoader.parent)
        0 * visitor._
    }

    void cannotSeeResource(String name) {
        assert classLoader.getResource(name) == null
        assert classLoader.getResourceAsStream(name) == null
        assert !classLoader.getResources(name).hasMoreElements()
    }

    void canSeeResource(String name) {
        assert classLoader.getResource(name) != null
        def instr = classLoader.getResourceAsStream(name)
        assert instr != null
        instr.close()
        assert classLoader.getResources(name).hasMoreElements()
    }

    void canSeePackage(String name) {
        assert classLoader.getPackage(name) != null
        assert classLoader.packages.any { it.name == name }
    }

    void cannotSeePackage(String name) {
        assert classLoader.getPackage(name) == null
        assert !classLoader.packages.any { it.name == name }
    }

    void canLoadClass(Class<?> clazz) {
        assert classLoader.loadClass(clazz.name, false).is(clazz)
        assert classLoader.loadClass(clazz.name).is(clazz)
    }

    void cannotLoadClass(Class<?> clazz) {
        try {
            classLoader.loadClass(clazz.name, false)
            fail()
        } catch (ClassNotFoundException expected) {}
        try {
            classLoader.loadClass(clazz.name)
            fail()
        } catch (ClassNotFoundException expected) {}
    }

    def "does not attempt to load not allowed class"() {
        given:
        def parent = Mock(ClassLoader, useObjenesis: false)
        withSpec(parent) { FilteringClassLoader.Spec spec ->
            spec.allowPackage("good")
        }

        when:
        classLoader.loadClass("good.Clazz")

        //noinspection GroovyAccessibility
        then:
        1 * parent.loadClass("good.Clazz", false) >> String
        0 * parent._

        when:
        classLoader.loadClass("bad.Clazz")

        then:
        thrown(ClassNotFoundException)

        and:
        0 * parent._
    }

    void "spec is copied correctly"() {
        given:
        def parent = Mock(ClassLoader, useObjenesis: false)
        def spec = new FilteringClassLoader.Spec([ 'allow.ClassName' ], [ 'allowPackage' ], [ 'allowPackagePrefix' ], [ 'allowPackageResource' ], [ 'allowResource' ], [ 'disallow.ClassName' ], [ 'disallowPackage' ])
        def filteringClassLoader = new FilteringClassLoader(parent, spec)
        def visitor = Mock(ClassLoaderVisitor)

        when:
        filteringClassLoader.visit(visitor)

        then:
        1 * visitor.visitSpec(spec)
        1 * visitor.visitParent(parent)
    }

    private void withSpec(ClassLoader parent = null, Closure cl) {
        if (parent == null) {
            parent = getClass().getClassLoader()
        }
        def spec = new FilteringClassLoader.Spec()
        cl(spec)
        this.classLoader = new FilteringClassLoader(parent, spec)
    }
}
