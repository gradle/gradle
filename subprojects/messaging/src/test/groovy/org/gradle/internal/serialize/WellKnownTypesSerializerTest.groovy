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
package org.gradle.internal.serialize

import groovy.transform.Canonical
import spock.lang.Unroll

class WellKnownTypesSerializerTest extends SerializerSpec {
    def serializer = new WellKnownTypesSerializer(this.class.classLoader)

    def canSerializeAndDeserializeObject() {
        GroovyClassLoader classLoader = new GroovyClassLoader(getClass().classLoader)
        WellKnownTypesSerializer serializer = new WellKnownTypesSerializer(classLoader)

        Class cl = classLoader.parseClass('package org.gradle.cache; class TestObj implements Serializable { }')
        Object o = cl.newInstance()

        when:
        def r = serialize(o, serializer)

        then:
        cl.isInstance(r)
    }

    @Unroll
    def "efficient serialization of #type"() {
        expect:
        usesEfficientSerialization(value, serializer, null) == value

        where:
        value << [
            null, 1, 2L, "foo", (short) 4, 2d, 3f, Foo.bar, Foo.baz,
            [1, 2],
            [a: 'foo', b: 23L],
            [Foo.bar, 2] as Set,
            [123, 45] as byte[],
            new File('foo'),
            true,
            false,
            [(true): [122: ['x', new File('foo')]]] as TreeMap,
            [new A(name: 'foo')]
        ]
        type = value!=null?value.getClass():'null'

    }

    enum Foo {
        bar,
        baz
    }

    @Canonical
    static class A implements Serializable {
        String name
    }
}
