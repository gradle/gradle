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

import org.gradle.internal.concurrent.CompositeStoppable
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.anything
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.*

@RunWith(JMock.class)
class DiffUtilTest {

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Test
    public void notifiesListenerOfElementAddedToSet() {
        ChangeListener<String> listener = context.mock(ChangeListener.class)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        context.checking {
            one(listener).added('c')
        }

        DiffUtil.diff(b, a, listener)
    }

    @Test
    public void notifiesListenerOfElementRemovedFromSet() {
        ChangeListener<String> listener = context.mock(ChangeListener.class)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        context.checking {
            one(listener).removed('c')
        }

        DiffUtil.diff(a, b, listener)
    }

    @Test
    public void notifiesListenerOfElementAddedToMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        context.checking {
            one(listener).added(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('c'))
                assertThat(entry.value, equalTo('value c'))
            }
        }

        DiffUtil.diff(b, a, listener)
    }

    @Test
    public void notifiesListenerOfElementRemovedFromMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        context.checking {
            one(listener).removed(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('c'))
                assertThat(entry.value, equalTo('value c'))
            }
        }

        DiffUtil.diff(a, b, listener)
    }

    @Test
    public void notifiesListenerOfChangedElementInMap() {
        ChangeListener<Map.Entry<String, String>> listener = context.mock(ChangeListener.class)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'new b']

        context.checking {
            one(listener).changed(withParam(anything()))
            will { entry ->
                assertThat(entry.key, equalTo('b'))
                assertThat(entry.value, equalTo('new b'))
            }
        }

        DiffUtil.diff(b, a, listener)
    }

    @Test
    public void sameObjectsEqual() {
        Object o1 = new Object()
        assertTrue(DiffUtil.checkEquality(o1, o1))
    }

    @Test
    public void equalObjectsEqual() {
        String s1 = new String("Foo")
        String s2 = new String("Foo")
        assertTrue(DiffUtil.checkEquality(s1, s2))
    }

    @Test
    public void notEqualObjectsNotEqual() {
        String s1 = new String("Foo")
        String s2 = new String("Bar")
        assertFalse(DiffUtil.checkEquality(s1, s2))
    }

    private enum LocalEnum1 {
        DUCK, GOOSE
    }

    private enum LocalEnum2 {
        DUCK, GOOSE
    }

    @Test
    public void sameEnumerationEqual() {
        assertTrue(DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum1.DUCK))
    }

    @Test
    public void sameEnumerationDifferentConstantsNotEqual() {
        assertFalse(DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum1.GOOSE))
    }

    @Test
    public void differentEnumerationsNotEqual() {
        assertFalse(DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum2.DUCK))
    }

    // We may want to change this behavior in the future, so don't be afraid to remove this test.
    @Test
    public void differentClassLoadersNotEqual() {
        List<Closeable> loaders = []

        try {
            Class<?> clazz1 = createClass(loaders)
            Class<?> clazz2 = createClass(loaders)

            assertNotSame(clazz1, clazz2)

            Object o1 = clazz1.newInstance()
            Object o2 = clazz2.newInstance()

            assertNotEquals(o1, o2)
            assertFalse(DiffUtil.checkEquality(o1, o2))
        } finally {
            CompositeStoppable.stoppable(loaders).stop()
        }
    }

    Class<?> createClass(List<Closeable> loaders) {
        def loader = new GroovyClassLoader(getClass().classLoader)
        loaders.add loader
        loader.parseClass """
            class MyClass {
            }
        """
    }

    @Test
    public void enumsWithDifferentClassLoadersEqual() {
        List<Closeable> loaders = []

        try {
            Class<?> clazz1 = createEnumClass(loaders)
            Class<?> clazz2 = createEnumClass(loaders)

            assertNotSame(clazz1, clazz2)

            Object o1 = Enum.valueOf(clazz1, "OTTER")
            Object o2 = Enum.valueOf(clazz2, "OTTER")

            Object s1 = Enum.valueOf(clazz1, "SEAL")
            Object s2 = Enum.valueOf(clazz2, "SEAL")

            assertNotEquals(o1, o2)
            assertTrue(DiffUtil.checkEquality(o1, o2))
            assertFalse(DiffUtil.checkEquality(o1, s1))
            assertFalse(DiffUtil.checkEquality(o1, s2))
            assertFalse(DiffUtil.checkEquality(o2, s1))
            assertFalse(DiffUtil.checkEquality(o2, s2))
        } finally {
            CompositeStoppable.stoppable(loaders).stop()
        }
    }

    Class<?> createEnumClass(List<Closeable> loaders) {
        def loader = new GroovyClassLoader(getClass().classLoader)
        loaders.add loader
        loader.parseClass """
            enum MuEnum {
                OTTER,
                SEAL
            }
        """
    }
}
