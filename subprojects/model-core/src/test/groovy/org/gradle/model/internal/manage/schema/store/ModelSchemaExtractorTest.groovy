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

package org.gradle.model.internal.manage.schema.store

import org.gradle.model.Managed
import org.gradle.model.collection.ManagedSet
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.manage.schema.ModelSchema
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

import static ManagedTypeModelSchemaExtractionStrategy.SUPPORTED_UNMANAGED_TYPES

class ModelSchemaExtractorTest extends Specification {

    def extractor = new ModelSchemaExtractor()

    static interface NotAnnotatedInterface {}

    def "has to be annotated with @Managed"() {
        expect:
        fail NotAnnotatedInterface, Pattern.quote("not a managed type")
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
        fail NoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: [foo])")
        fail HasExtraNonPropertyMethods, Pattern.quote("only paired getter/setter methods are supported (invalid methods: [foo])")
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
        when:
        def properties = extract(SingleProperty).properties

        then:
        properties.size() == 1
        properties.name.type == ModelType.of(String)
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

    def "only selected unmanaged property types are allowed"() {
        expect:
        fail NonStringProperty,
                Pattern.quote("$Object.name is not a supported property type, only managed and the following unmanaged types are supported: ${SUPPORTED_UNMANAGED_TYPES.join(", ")} (method: getName)")
    }

    @Managed
    static interface BytePrimitiveProperty {
        byte getByteProperty()

        void setByteProperty(byte value)
    }

    def "byte property types are not allowed and there is no suggested replacement"() {
        expect:
        fail BytePrimitiveProperty,
                Pattern.quote("byte is not a supported property type, only managed and the following unmanaged types are supported: ${SUPPORTED_UNMANAGED_TYPES.join(", ")} (method: getByteProperty)")
    }

    @Unroll
    def "boxed types are suggested when primitive types are being used - #primitiveType"() {
        when:
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $primitiveType getPrimitiveProperty()

                void setPrimitiveProperty($primitiveType value)
            }
        """

        then:
        fail interfaceWithPrimitiveProperty, Pattern.quote("$primitiveType is not a supported property type, use $boxedType.name instead (method: getPrimitiveProperty)")

        where:
        primitiveType | boxedType
        "boolean"     | Boolean
        "char"        | Integer
        "float"       | Double
        "long"        | Long
        "short"       | Integer
        "int"         | Integer
        "double"      | Double

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
        def properties = extract(MultipleProps).properties.values()

        then:
        properties*.name == ["prop1", "prop2", "prop3"]
        properties*.type == [ModelType.of(String)] * 3
    }

    @Managed
    static interface ManagedPropertyWithSetter {
        SingleProperty getManaged()

        void setManaged(SingleProperty name)
    }

    @Managed
    interface SelfReferencing {
        SelfReferencing getSelf()
    }

    def "can extract self referencing type"() {
        expect:
        extract(SelfReferencing).properties.self.type == ModelType.of(SelfReferencing)
    }

    @Managed
    interface A1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    @Managed
    interface B1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    @Managed
    interface C1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    @Managed
    interface D1 {
        A1 getA();

        B1 getB();

        C1 getC();

        D1 getD();
    }

    def "can extract incestuous nest"() {
        expect:
        extract(type).properties.a.type == extract(A1).type
        extract(type).properties.b.type == extract(B1).type
        extract(type).properties.c.type == extract(C1).type
        extract(type).properties.d.type == extract(D1).type

        where:
        type << [A1, B1, C1, D1]
    }


    def "type argument of a managed set has to be specified"() {
        given:
        def type = new ModelType<ManagedSet>() {}

        expect:
        fail type, "type parameter of $ManagedSet.name has to be specified"
    }

    @Unroll
    def "type argument of a managed set cannot be a wildcard - #type"() {
        expect:
        fail type, "type parameter of $ManagedSet.name cannot be a wildcard"

        where:
        type << [
                new ModelType<ManagedSet<?>>() {},
                new ModelType<ManagedSet<? extends A1>>() {},
                new ModelType<ManagedSet<? super A1>>() {}
        ]
    }

    def "type argument of a managed set has to be managed"() {
        given:
        def type = new ModelType<ManagedSet<Object>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $Object.name: not a managed type. The type was analyzed due to the following dependencies:
$type
\\--- $Object.name""")
    }

    def "type argument of a managed set has to be a valid managed type"() {
        given:
        def type = new ModelType<ManagedSet<SetterOnly>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $SetterOnly.name: only paired getter/setter methods are supported (invalid methods: [setName]). The type was analyzed due to the following dependencies:
$type
\\--- $SetterOnly.name""")
    }

    interface SpecialManagedSet<T> extends ManagedSet<T> {}

    def "extensions managed sets are not supported"() {
        given:
        def type = new ModelType<SpecialManagedSet<A1>>() {}

        expect:
        fail type, "subtyping $ManagedSet.name is not supported"
    }

    def "managed sets of managed set are not supported"() {
        given:
        def type = new ModelType<ManagedSet<ManagedSet<A1>>>() {}

        expect:
        fail type, "$ManagedSet.name cannot be used as type parameter of $ManagedSet.name"
    }

    private ModelSchema<?> extract(ModelType<?> modelType) {
        extractor.extract(modelType, new ModelSchemaCache())
    }

    private ModelSchema<?> extract(Class<?> clazz) {
        extract(ModelType.of(clazz))
    }

    private void fail(def clazzOrModelType, String msgPattern) {
        try {
            extract(clazzOrModelType)
            throw new AssertionError("schema extraction from ${getName(clazzOrModelType)} should failed with message: $msgPattern")
        } catch (InvalidManagedModelElementTypeException e) {
            assert e.message.startsWith("Invalid managed model type ${getName(clazzOrModelType)}: ")
            assert e.message =~ msgPattern
        }
    }

    private String getName(ModelType<?> modelType) {
        modelType
    }

    private String getName(Class<?> clazz) {
        clazz.name
    }
}
