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
package org.gradle.util

import org.hamcrest.Matcher
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Before

@RunWith(JMock.class)
class FilteringClassLoaderTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final FilteringClassLoader classLoader = new FilteringClassLoader(FilteringClassLoaderTest.class.getClassLoader())

    @Test
    void passesThroughSystemClasses() {
        assertThat(classLoader.loadClass(String.class.name), sameInstance(String.class))
    }

    @Test
    void passesThroughSystemPackages() {
        assertThat(classLoader.getPackage('java.lang'), notNullValue(Package.class))
        assertThat(classLoader.getPackages(), hasPackage('java.lang'))
    }

    private Matcher<Package[]> hasPackage(String name) {
        Matcher matcher = [matches: {Package p -> p.name == name }, describeTo: {description -> description.appendText("has package '$name'")}] as Matcher
        return hasItemInArray(matcher)
    }

    @Test
    void passesThroughSystemResources() {
        assertThat(classLoader.getResource('com/sun/jndi/ldap/jndiprovider.properties'), notNullValue())
        assertThat(classLoader.getResourceAsStream('com/sun/jndi/ldap/jndiprovider.properties'), notNullValue())
        assertTrue(classLoader.getResources('com/sun/jndi/ldap/jndiprovider.properties').hasMoreElements())
    }

    @Test
    void filtersClasses() {
        classLoader.parent.loadClass(Test.class.name)

        try {
            classLoader.loadClass(Test.class.name, false)
            fail()
        } catch (ClassNotFoundException e) {
            assertThat(e.message, equalTo("$Test.name not found.".toString()))
        }
        try {
            classLoader.loadClass(Test.class.name)
            fail()
        } catch (ClassNotFoundException e) {
            assertThat(e.message, equalTo("$Test.name not found.".toString()))
        }
    }

    @Test
    void filtersPackages() {
        assertThat(classLoader.parent.getPackage('org.junit'), notNullValue())

        assertThat(classLoader.getPackage('org.junit'), nullValue())
        assertThat(classLoader.getPackages(), not(hasPackage('org.junit')))
    }

    @Test
    void filtersResources() {
        assertThat(classLoader.parent.getResource('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), nullValue())
        assertThat(classLoader.getResourceAsStream('org/gradle/util/ClassLoaderTest.txt'), nullValue())
        assertFalse(classLoader.getResources('org/gradle/util/ClassLoaderTest.txt').hasMoreElements())
    }

    @Test
    void passesThroughClassesInSpecifiedPackages() {
        classLoader.allowPackage('org.junit')
        assertThat(classLoader.loadClass(Test.class.name), sameInstance(Test.class))
        assertThat(classLoader.loadClass(Test.class.name, false), sameInstance(Test.class))
        assertThat(classLoader.loadClass(BlockJUnit4ClassRunner.class.name), sameInstance(BlockJUnit4ClassRunner.class))
    }

    @Test
    void passesThroughSpecifiedClasses() {
        classLoader.allowClass(Test.class)
        assertThat(classLoader.loadClass(Test.class.name), sameInstance(Test.class))
        try {
            classLoader.loadClass(Before.class.name)
            fail()
        } catch (ClassNotFoundException e) {
            // expected
        }
    }

    @Test
    void filtersSpecifiedClasses() {
        classLoader.allowPackage("org.junit")
        classLoader.disallowClass("org.junit.Test")

        canLoadClass(Before)
        cannotLoadClass(Test)
    }

    @Test
    void disallowClassWinsOverAllowClass() {
        classLoader.allowClass(Test)
        classLoader.disallowClass(Test.name)

        cannotLoadClass(Test)
    }

    @Test
    void passesThroughSpecifiedPackages() {
        assertThat(classLoader.getPackage('org.junit'), nullValue())
        assertThat(classLoader.getPackages(), not(hasPackage('org.junit')))

        classLoader.allowPackage('org.junit')

        assertThat(classLoader.getPackage('org.junit'), notNullValue())
        assertThat(classLoader.getPackages(), hasPackage('org.junit'))
        assertThat(classLoader.getPackage('org.junit.runner'), notNullValue())
        assertThat(classLoader.getPackages(), hasPackage('org.junit.runner'))
    }

    @Test
    void passesThroughResourcesInSpecifiedPackages() {
        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), nullValue())

        classLoader.allowPackage('org.gradle')

        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertThat(classLoader.getResourceAsStream('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertTrue(classLoader.getResources('org/gradle/util/ClassLoaderTest.txt').hasMoreElements())
    }

    @Test
    void passesThroughResourcesWithSpecifiedPrefix() {
        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), nullValue())

        classLoader.allowResources('org/gradle')

        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertThat(classLoader.getResourceAsStream('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertTrue(classLoader.getResources('org/gradle/util/ClassLoaderTest.txt').hasMoreElements())
    }

    @Test
    void passesThroughSpecifiedResources() {
        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), nullValue())

        classLoader.allowResource('org/gradle/util/ClassLoaderTest.txt')

        assertThat(classLoader.getResource('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertThat(classLoader.getResourceAsStream('org/gradle/util/ClassLoaderTest.txt'), notNullValue())
        assertTrue(classLoader.getResources('org/gradle/util/ClassLoaderTest.txt').hasMoreElements())
    }

    void canLoadClass(Class<?> clazz) {
        assert classLoader.loadClass(clazz.name) == clazz
    }

    void cannotLoadClass(Class<?> clazz) {
        try {
            classLoader.loadClass(clazz.name)
            fail()
        } catch (ClassNotFoundException expected) {}
    }
}
