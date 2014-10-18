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

package org.gradle.model.internal.manage.schema.extraction

import org.gradle.model.Managed
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.manage.schema.ModelSchema
import spock.lang.Specification
import spock.lang.Unroll

class ModelSchemaExtractorTest extends Specification {

    def extractor = new ModelSchemaExtractor(new DefaultModelSchemaStore(null), null)

    static interface NotAnnotatedInterface {}

    def "has to be annotated with @Managed"() {
        expect:
        fail NotAnnotatedInterface, "must be annotated with $Managed.name"
    }

    @Managed
    static class EmptyStaticClass {}

    def "must be interface"() {
        expect:
        fail EmptyStaticClass, "must be defined as an interface"
    }

    @Managed
    static interface EmptyInterfaceWithParent extends Serializable {}

    def "cannot extend"() {
        expect:
        fail EmptyInterfaceWithParent, "cannot extend other types"
    }

    @Managed
    static interface ParameterizedEmptyInterface<T> {}

    def "cannot parameterize"() {
        expect:
        fail ParameterizedEmptyInterface, "cannot be a parameterized type"
    }

    @Managed
    static interface NoGettersOrSetters {
        void foo(String bar)
    }

    @Managed
    static interface HasExtraNonPropertyMethods {
        String getName()

        void setName(String name)

        void foo(String bar)
    }

    def "can only have getters and setters"() {
        expect:
        fail NoGettersOrSetters, "only paired getter/setter methods are supported \\(invalid methods: \\[foo\\]\\)"
        fail HasExtraNonPropertyMethods, "only paired getter/setter methods are supported \\(invalid methods: \\[foo\\]\\)"
    }

    @Managed
    static interface OnlyGetter {
        String getName()
    }

    def "must be symmetrical"() {
        expect:
        fail OnlyGetter, "no corresponding setter"
    }

    @Managed
    static interface SingleProperty {
        String getName()

        void setName(String name)
    }

    def "extract single property"() {
        expect:
        extract(SingleProperty).properties.size() == 1
        extract(SingleProperty).properties["name"].type == ModelType.of(String)
    }

    @Managed
    static interface GetterWithParams {
        String getName(String name)

        void setName(String name)

    }

    def "malformed getter"() {
        expect:
        fail GetterWithParams, "getter methods cannot take parameters"
    }

    @Managed
    static interface NonVoidSetter {
        String getName()

        String setName(String name)
    }

    def "non void setter"() {
        expect:
        fail NonVoidSetter, "setter method must have void return type"
    }

    @Managed
    static interface SetterWithExtraParams {
        String getName()

        void setName(String name, String otherName)
    }

    def "setter with extra params"() {
        expect:
        fail SetterWithExtraParams, "setter method must have exactly one parameter"
    }

    @Managed
    static interface MisalignedSetterType {
        String getName()

        void setName(Object name)
    }

    def "misaligned setter type"() {
        expect:
        fail MisalignedSetterType, "setter method param must be of exactly the same type"
    }

    @Managed
    static interface SetterOnly {
        void setName(String name);
    }

    def "setter only"() {
        expect:
        fail SetterOnly, "only paired getter/setter methods are supported"
    }

    @Managed
    static interface NonStringProperty {
        Object getName()

        void setName(Object name)
    }

    def "only String and managed properties are allowed"() {
        expect:
        fail NonStringProperty, /only String and managed properties are supported \(method: getName\)/
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

    def "multiple properties"() {
        when:
        def schema = extract(MultipleProps)

        then:
        schema.properties.values().toList()*.name == ["prop1", "prop2", "prop3"]
        schema.properties.values().toList()*.type == [ModelType.of(String)] * 3
    }

    @Managed
    static interface ManagedPropertyWithSetter {
        SingleProperty getManaged()

        void setManaged(SingleProperty name)
    }

    @Unroll
    def "is managed - #clazz.simpleName"() {
        expect:
        extractor.isManaged(clazz) == expected

        where:
        clazz                 | expected
        SingleProperty        | true
        NotAnnotatedInterface | false
    }

    private ModelSchema<?> extract(Class<?> clazz) {
        extractor.extract(clazz)
    }

    private void fail(Class<?> clazz, String msgPattern) {
        try {
            extract(clazz)
            throw new AssertionError("schema extraction from $clazz should failed with message: $msgPattern")
        } catch (InvalidManagedModelElementTypeException e) {
            assert e.message =~ msgPattern
        }
    }

}
