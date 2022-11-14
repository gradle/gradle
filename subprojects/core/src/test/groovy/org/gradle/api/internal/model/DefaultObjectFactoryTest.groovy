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

import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultObjectFactoryTest extends Specification {
    def propertyFactory = new DefaultPropertyFactory(Stub(PropertyHost))
    def patternSetFactory = TestFiles.patternSetFactory
    def factory = new DefaultObjectFactory(TestUtil.instantiatorFactory().decorateLenient(), TestUtil.objectInstantiator(), Stub(DirectoryFileTreeFactory), patternSetFactory, propertyFactory, Stub(FilePropertyFactory), Stub(TaskDependencyFactory), Stub(FileCollectionFactory), Stub(DomainObjectCollectionFactory))

    def "property has no value"() {
        expect:
        def property = factory.property(Boolean)
        !property.present

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the value of this property because it has no value available.'
    }

    def "cannot create property for null value"() {
        when:
        factory.property(null)

        then:
        def t = thrown(IllegalArgumentException)
        t.message == 'Class cannot be null'
    }

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

    def "list property has empty list as value"() {
        expect:
        def property = factory.listProperty(String)
        property.present
        property.get().empty
    }

    def "can create list property with primitive type"() {
        given:
        def property = factory.listProperty(type)

        expect:
        property.elementType == boxedType

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

    def "set property has empty set as value"() {
        expect:
        def property = factory.setProperty(String)
        property.present
        property.get().empty
    }

    def "can create set property with primitive type"() {
        given:
        def property = factory.setProperty(type)

        expect:
        property.elementType == boxedType

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

    def "map property has empty map as value"() {
        expect:
        def property = factory.mapProperty(String, Boolean)
        property.present
        property.get().isEmpty()
    }

    def "can create map property with primitive type"() {
        given:
        def property = factory.mapProperty(type, type)

        expect:
        property.keyType == boxedType
        property.valueType == boxedType

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

}
