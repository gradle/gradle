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

package org.gradle.api.internal.model

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification
import spock.lang.Unroll


class DefaultObjectFactoryTest extends Specification {
    def factory = new DefaultObjectFactory(Stub(Instantiator), Stub(NamedObjectInstantiator), Stub(FileResolver), Stub(DirectoryFileTreeFactory))

    def "can create a property"() {
        expect:
        def property = factory.property(Boolean)
        property.present
        !property.get()
    }

    def "cannot create property for null value"() {
        when:
        factory.property(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Class cannot be null'
    }

    @Unroll
    def "properties with wrapper type #type provide default value"() {
        given:
        def property = factory.property(type)

        expect:
        property.get() == defaultValue

        where:
        type      | defaultValue
        Boolean   | false
        Byte      | 0
        Short     | 0
        Integer   | 0
        Long      | 0L
        Float     | 0.0f
        Double    | 0.0d
        Character | '\u0000'
    }

    @Unroll
    def "properties wih primitive type #type provide default value"() {
        given:
        def property = factory.property(type)

        expect:
        property.get() == defaultValue
        property.type == boxedType

        where:
        type           | boxedType | defaultValue
        Boolean.TYPE   | Boolean   | false
        Byte.TYPE      | Byte      | 0
        Short.TYPE     | Short     | 0
        Integer.TYPE   | Integer   | 0
        Long.TYPE      | Long      | 0L
        Float.TYPE     | Float     | 0.0f
        Double.TYPE    | Double    | 0.0d
        Character.TYPE | Character | '\u0000'
    }

    def "creating property type for reference type throws exception upon retrieval of value"() {
        when:
        def property = factory.property(Runnable)
        property.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == 'No value has been specified for this provider.'
    }

    def "can create SourceDirectorySet"() {
        expect:
        factory.sourceDirectorySet("name", "display") != null
    }

    def "can create a List property"() {
        expect:
        def property = factory.listProperty(String)
        property.present
        property.get() == []
    }

    def "can create a Set property"() {
        expect:
        def property = factory.setProperty(String)
        property.present
        property.get() == [] as Set
    }

}
