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
import spock.lang.Specification

class DiffUtilTest extends Specification {

    def "notifies listener for element added to set"() {
        ChangeListener<String> listener = Mock(ChangeListener)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        when:
        DiffUtil.diff(b, a, listener)
        then:
        1 * listener.added('c')
    }

    def "notifies listener of element removed from set"() {
        ChangeListener<String> listener = Mock(ChangeListener)
        Set<String> a = ['a', 'b'] as Set
        Set<String> b = ['a', 'b', 'c'] as Set

        when:
        DiffUtil.diff(a, b, listener)
        then:
        1 * listener.removed('c')
    }

    def "notifies listener of element added to map"() {
        ChangeListener<Map.Entry<String, String>> listener = Mock(ChangeListener)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        when:
        DiffUtil.diff(b, a, listener)
        then:
        1 * listener.added(_) >> { Map.Entry entry ->
            assert entry.key == 'c'
            assert entry.value == 'value c'
        }
    }

    def "notifies listener of element removed from map"() {
        ChangeListener<Map.Entry<String, String>> listener = Mock(ChangeListener)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'value b', c: 'value c']

        when:
        DiffUtil.diff(a, b, listener)
        then:
        1 * listener.removed(_) >> { Map.Entry entry ->
            assert entry.key == 'c'
            assert entry.value == 'value c'
        }
    }

    def "notifies listener of changed element in map"() {
        ChangeListener<Map.Entry<String, String>> listener = Mock(ChangeListener)
        Map<String, String> a = [a: 'value a', b: 'value b']
        Map<String, String> b = [a: 'value a', b: 'new b']

        when:
        DiffUtil.diff(b, a, listener)
        then:
        1 * listener.changed(_) >> { Map.Entry entry ->
            assert entry.key == 'b'
            assert entry.value == 'new b'
        }
    }

    def "same objects equal"() {
        Object o1 = new Object()
        expect:
        DiffUtil.checkEquality(o1, o1)
    }

    def "equal objects equal"() {
        String s1 = new String("Foo")
        String s2 = new String("Foo")
        expect:
        DiffUtil.checkEquality(s1, s2)
    }

    def "non-equal objects do not equal"() {
        String s1 = new String("Foo")
        String s2 = new String("Bar")
        expect:
        !DiffUtil.checkEquality(s1, s2)
    }

    private enum LocalEnum1 {
        DUCK, GOOSE
    }

    private enum LocalEnum2 {
        DUCK, GOOSE
    }

    def "same enum equal"() {
        expect:
        DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum1.DUCK)
    }

    def "same enum different constants do not equal"() {
        expect:
        !DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum1.GOOSE)
    }

    def "different enums do not equal"() {
        expect:
        !DiffUtil.checkEquality(LocalEnum1.DUCK, LocalEnum2.DUCK)
    }

    def "different class loaders do not equal"() {
        List<Closeable> loaders = []

        try {
            Class<?> clazz1 = createClass(loaders)
            Class<?> clazz2 = createClass(loaders)

            assert clazz1 != clazz2

            Object o1 = clazz1.newInstance()
            Object o2 = clazz2.newInstance()

            expect:
            o1 != o2
            !DiffUtil.checkEquality(o1, o2)
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

    def "enums with different class-loaders equal"() {
        List<Closeable> loaders = []

        try {
            Class<?> clazz1 = createEnumClass(loaders)
            Class<?> clazz2 = createEnumClass(loaders)

            assert clazz1 != clazz2

            Object o1 = Enum.valueOf(clazz1, "OTTER")
            Object o2 = Enum.valueOf(clazz2, "OTTER")

            Object s1 = Enum.valueOf(clazz1, "SEAL")
            Object s2 = Enum.valueOf(clazz2, "SEAL")

            expect:
            o1 != o2
            DiffUtil.checkEquality(o1, o2)
            !DiffUtil.checkEquality(o1, s1)
            !DiffUtil.checkEquality(o1, s2)
            !DiffUtil.checkEquality(o2, s1)
            !DiffUtil.checkEquality(o2, s2)
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
