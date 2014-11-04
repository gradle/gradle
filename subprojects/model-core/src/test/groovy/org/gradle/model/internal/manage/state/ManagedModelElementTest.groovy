/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.state

import org.gradle.model.Managed
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.manage.instance.DefaultModelInstantiator
import org.gradle.model.internal.manage.instance.ManagedModelElement
import org.gradle.model.internal.manage.instance.UnexpectedModelPropertyTypeException
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import spock.lang.Specification
import spock.lang.Unroll

import java.beans.Introspector

class ManagedModelElementTest extends Specification {

    def schemas = new DefaultModelSchemaStore()
    def instantiator = new DefaultModelInstantiator(schemas)

    def <T> ManagedModelElement<T> createElement(Class<T> elementClass) {
        new ManagedModelElement<MultipleProps>(schemas.getSchema(ModelType.of(elementClass)))
    }

    def <T> T createInstance(Class<T> elementClass) {
        instantiator.newInstance(schemas.getSchema(ModelType.of(elementClass)))
    }

    @Managed
    static interface MultipleProps {
        String getProp1();

        void setProp1(String string);

        String getProp2();

        void setProp2(String string);

        String getProp3();

        void setProp3(String string);
    }

    def "can create managed element"() {
        given:
        def element = createElement(MultipleProps)

        when:
        element.get(ModelType.of(String), "prop1").set("foo")

        then:
        element.get(ModelType.of(String), "prop1").get() == "foo"
    }

    def "an error is raised when property type is different than requested"() {
        given:
        def element = createElement(MultipleProps)

        when:
        element.get(ModelType.of(Object), "prop1")

        then:
        UnexpectedModelPropertyTypeException e = thrown()
        e.message == "Expected property 'prop1' for type '$MultipleProps.name' to be of type '$Object.name' but it actually is of type '$String.name'"
    }

    def "can get an instance of a managed element"() {
        given:
        def instance = createInstance(MultipleProps)

        when:
        instance.prop1 = "foo"

        then:
        instance.prop1 == "foo"
    }

    @Managed
    interface AllSupportedUnmanagedTypes {
        Boolean getBooleanProperty()

        void setBooleanProperty(Boolean value)

        Integer getIntegerProperty()

        void setIntegerProperty(Integer value)

        Long getLongProperty()

        void setLongProperty(Long value)

        Double getDoubleProperty()

        void setDoubleProperty(Double value)

        BigInteger getBigIntegerProperty()

        void setBigIntegerProperty(BigInteger value)

        BigDecimal getBigDecimalProperty()

        void setBigDecimalProperty(BigDecimal value)

        String getStringProperty()

        void setStringProperty(String value)
    }

    @Unroll
    def "can set/get properties of all supported unmanaged types - #propertyClass.simpleName"() {
        given:
        def instance = createInstance(AllSupportedUnmanagedTypes)

        expect:
        instance[propertyName] == null

        when:
        instance[propertyName] = value

        then:
        instance[propertyName] == value

        where:
        propertyClass | value
        Boolean       | Boolean.TRUE
        Integer       | new Integer(0)
        Long          | new Long(1L)
        Double        | new Double(2.2)
        BigInteger    | new BigInteger("3")
        BigDecimal    | new BigDecimal(4)
        String        | "test"

        propertyName = "${Introspector.decapitalize(propertyClass.simpleName)}Property"
    }
}
