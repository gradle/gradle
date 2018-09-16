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

import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification
import spock.lang.Unroll


class DefaultObjectFactoryTest extends Specification {
    def factory = new DefaultObjectFactory(Stub(Instantiator), Stub(NamedObjectInstantiator), Stub(FileResolver), Stub(DirectoryFileTreeFactory), Stub(FilePropertyFactory))

    def "can create a property"() {
        expect:
        def property = factory.property(Boolean)
        !property.present

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'
    }

    def "cannot create property for null value"() {
        when:
        factory.property(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Class cannot be null'
    }

    @Unroll
    def "can create property with primitive type"() {
        given:
        def property = factory.property(type)

        expect:
        property.type == boxedType
        !property.present

        where:
        type           | boxedType
        Boolean.TYPE   | Boolean
        Byte.TYPE      | Byte
        Short.TYPE     | Short
        Integer.TYPE   | Integer
        Long.TYPE      | Long
        Float.TYPE     | Float
        Double.TYPE    | Double
        Character.TYPE | Character
    }

    def "can create SourceDirectorySet"() {
        expect:
        factory.sourceDirectorySet("name", "display") != null
    }

    def "can create a List property"() {
        expect:
        def property = factory.listProperty(String)
        !property.present
        property.getOrNull() == null
    }

    def "can create a Set property"() {
        expect:
        def property = factory.setProperty(String)
        !property.present
        property.getOrNull() == null
    }

}
