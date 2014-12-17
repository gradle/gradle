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

package org.gradle.model.internal.manage.schema.extract

import org.gradle.model.Managed
import org.gradle.model.Unmanaged
import org.gradle.model.collection.ManagedSet
import org.gradle.model.internal.manage.schema.ModelSchema
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache
import org.gradle.model.internal.type.ModelType
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

class ModelSchemaExtractorTest extends Specification {

    def extractor = new ModelSchemaExtractor()

    static interface NotAnnotatedInterface {}

    def "unmanaged type"() {
        expect:
        extract(NotAnnotatedInterface).kind == ModelSchema.Kind.UNMANAGED
    }

    @Managed
    static class EmptyStaticClass {}

    def "must be interface"() {
        expect:
        fail EmptyStaticClass, "must be defined as an interface"
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
        fail NoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").owner(NoGettersOrSetters).takes(String)})")
        fail HasExtraNonPropertyMethods, Pattern.quote("nly paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").owner(HasExtraNonPropertyMethods).takes(String)})")
    }

    @Managed
    static interface OnlyGetter {
        String getName()
    }

    def "must be symmetrical"() {
        expect:
        fail OnlyGetter, "read only property 'name' has non managed type java.lang.String, only managed types can be used"
    }

    @Managed
    static interface SingleStringNameProperty {
        String getName()

        void setName(String name)
    }

    def "extract single property"() {
        when:
        def properties = extract(SingleStringNameProperty).properties

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
        fail NonStringProperty, Pattern.quote("an unmanaged type")
    }

    @Managed
    static interface BytePrimitiveProperty {
        byte getByteProperty()

        void setByteProperty(byte value)
    }

    def "byte property types are not allowed and there is no suggested replacement"() {
        expect:
        fail BytePrimitiveProperty, Pattern.quote("an unmanaged type")
    }

    @Unroll
    def "boxed types are suggested when primitive types are being used - #primitiveType"() {
        when:
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $primitiveType.name getPrimitiveProperty()

                void setPrimitiveProperty($primitiveType.name value)
            }
        """

        then:
        fail interfaceWithPrimitiveProperty, primitiveType, Pattern.quote("type is not supported, please use $boxedType.name instead")

        where:
        primitiveType | boxedType
        boolean.class | Boolean
        char.class    | Integer
        float.class   | Double
        long.class    | Long
        short.class   | Integer
        int.class     | Integer
        double.class  | Double
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

    @Managed
    static interface WithInheritedProperties extends SingleStringNameProperty {
        Integer getCount()

        void setCount(Integer count)
    }

    def "extracts inherited properties"() {
        when:
        def properties = extract(WithInheritedProperties).properties.values()

        then:
        properties*.name == ["count", "name"]
    }

    static interface SingleIntegerValueProperty {
        Integer getValue()

        void setValue(Integer count)
    }

    @Managed
    static interface WithMultipleParents extends SingleStringNameProperty, SingleIntegerValueProperty {
    }

    def "extracts properties from multiple parents"() {
        when:
        def properties = extract(WithMultipleParents).properties.values()

        then:
        properties*.name == ["name", "value"]
    }

    static interface SinglePropertyNotAnnotated {
        String getName()

        void setName(String name)
    }

    @Managed
    static interface WithInheritedPropertiesFromGrandparent extends WithInheritedProperties {
        Boolean getFlag()

        void setFlag(Boolean flag)
    }

    def "extracts properties from multiple levels of inheritance"() {
        when:
        def properties = extract(WithInheritedPropertiesFromGrandparent).properties.values()

        then:
        properties*.name == ["count", "flag", "name"]
    }

    @Managed
    static interface WithInheritedPropertiesFromNotAnnotated extends SinglePropertyNotAnnotated {
        Integer getCount()

        void setCount(Integer count)
    }

    def "can extract inherited properties from an interface not annotated with @Managed"() {
        when:
        def properties = extract(WithInheritedPropertiesFromNotAnnotated).properties.values()

        then:
        properties*.name == ["count", "name"]
    }

    @Managed
    static interface SingleStringValueProperty {
        String getValue()

        void setValue(String value)
    }

    static interface SingleFloatValueProperty {
        Float getValue()

        void setValue(Float value)
    }

    @Managed
    static interface ConflictingPropertiesInParents extends SingleIntegerValueProperty, SingleStringValueProperty, SingleFloatValueProperty {
    }

    def "conflicting properties of super types are detected"() {
        given:
        def invalidMethods = [
                MethodDescription.name("getValue").owner(SingleFloatValueProperty).returns(Float),
                MethodDescription.name("getValue").owner(SingleIntegerValueProperty).returns(Integer),
                MethodDescription.name("getValue").owner(SingleStringValueProperty).returns(String),
        ]
        def message = Pattern.quote("overloaded methods are not supported (invalid methods: ${invalidMethods.join(", ")})")

        expect:
        fail ConflictingPropertiesInParents, message
    }

    static interface AnotherSingleStringValueProperty {
        String getValue()

        void setValue(String value)
    }

    @Managed
    static interface SamePropertyInMultipleTypes extends SingleStringValueProperty, AnotherSingleStringValueProperty {
    }

    def "exact same properties defined in multiple types of the hierarchy are allowed"() {
        when:
        def properties = extract(SamePropertyInMultipleTypes).properties.values()

        then:
        properties*.name == ["value"]
    }

    static interface ReadOnlyProperty {
        SingleStringValueProperty getSingleStringValueProperty()
    }

    @Managed
    static interface WritableProperty extends ReadOnlyProperty {
        void setSingleStringValueProperty(SingleStringValueProperty value)
    }

    def "read only property of a super type can be made writable"() {
        when:
        def properties = extract(WritableProperty).properties.values()

        then:
        properties*.writable == [true]
    }

    @Managed
    static interface ChildWithNoGettersOrSetters extends NoGettersOrSetters {
    }

    def "invalid methods of super types are reported"() {
        expect:
        fail ChildWithNoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").owner(NoGettersOrSetters).takes(String)})")
    }

    def "type argument of a managed set has to be specified"() {
        given:
        def type = ModelType.returnType(TypeHolder.getDeclaredMethod("noParam"))

        expect:
        fail type, "type parameter of $ManagedSet.name has to be specified"
    }

    static interface TypeHolder {
        ManagedSet noParam();
    }

    @Managed
    interface Thing {}

    @Managed
    interface SpecialThing extends Thing {}

    interface SimpleModel {
        Thing getThing()
    }

    @Managed
    interface SpecialModel extends SimpleModel {
        SpecialThing getThing()

        void setThing(SpecialThing thing)
    }

    def "a subclass may specialize a property type"() {
        when:
        def properties = extract(SpecialModel).properties.values()

        then:
        properties*.type == [ModelType.of(SpecialThing)]
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
        e.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type ${new ModelType<ManagedSet<Object>>() {}}: cannot create a managed set of type $Object.name as it is an unmanaged type.
Supported types:
 - enum types
 - JDK value types: String, Boolean, Integer, Long, Double, BigInteger, BigDecimal
 - org.gradle.model.collection.ManagedSet<?> of a managed type
 - interfaces annotated with org.gradle.model.Managed""")
    }

    def "type argument of a managed set has to be a valid managed type"() {
        given:
        def type = new ModelType<ManagedSet<SetterOnly>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        def invalidMethodDescription = MethodDescription.name("setName").owner(SetterOnly).takes(String)
        e.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $SetterOnly.name: only paired getter/setter methods are supported (invalid methods: ${invalidMethodDescription}).
The type was analyzed due to the following dependencies:
$type
  \\--- element type ($SetterOnly.name)""")
    }

    def "specializations of managed set are not supported"() {
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

    static class MyBigInteger extends BigInteger {
        MyBigInteger(String s) {
            super(s)
        }
    }

    static class MyBigDecimal extends BigDecimal {
        MyBigDecimal(String s) {
            super(s)
        }
    }

    def "cannot subclass non final value type"() {
        expect:
        fail MyBigInteger, Pattern.quote("subclasses of java.math.BigInteger are not supported")
        fail MyBigDecimal, Pattern.quote("subclasses of java.math.BigDecimal are not supported")
    }

    static enum MyEnum {
        A, B, C
    }

    def "can extract enum"() {
        expect:
        extract(MyEnum).kind == ModelSchema.Kind.VALUE
        extract(MyEnum).properties.isEmpty()
    }

    @Managed
    static interface HasUnmanagedOnManaged {
        @Unmanaged
        MyEnum getMyEnum();

        void setMyEnum(MyEnum myEnum)
    }

    def "cannot annotate managed type property with unmanaged"() {
        expect:
        fail HasUnmanagedOnManaged, Pattern.quote("property 'myEnum' is marked as @Unmanaged, but is of @Managed type")
    }

    @Managed
    static interface MissingUnmanaged {
        InputStream getThing();

        void setThing(InputStream inputStream);
    }

    def "unamanaged types must be annotated with unmanaged"() {
        expect:
        fail MissingUnmanaged, Pattern.quote("it is an unmanaged type (please annotate the getter with @org.gradle.model.Unmanaged if you want this property to be unmanaged)")
    }

    @Managed
    static interface ExtendsMissingUnmanaged {
        @Unmanaged
        InputStream getThing();

        void setThing(InputStream inputStream);
    }

    private void fail(extractType, String msgPattern) {
        fail(extractType, extractType, msgPattern)
    }

    def "subtype can declare property unmanaged"() {
        expect:
        extract(ExtendsMissingUnmanaged).properties.get("thing").type.rawClass == InputStream
    }

    @Managed
    static interface NoSetterForUnmanaged {
        @Unmanaged
        InputStream getThing();
    }

    def "must have setter for unmanaged"() {
        expect:
        fail NoSetterForUnmanaged, Pattern.quote("unmanaged property 'thing' cannot be read only, unmanaged properties must have setters")
    }

    @Managed
    static interface AddsSetterToNoSetterForUnmanaged extends NoSetterForUnmanaged {
        void setThing(InputStream inputStream);
    }

    def "subtype can add unmanaged setter"() {
        expect:
        extract(AddsSetterToNoSetterForUnmanaged).properties.get("thing").type.rawClass == InputStream
    }

    private void fail(extractType, errorType, String msgPattern) {
        try {
            extract(extractType)
            throw new AssertionError("schema extraction from ${getName(extractType)} should failed with message: $msgPattern")
        } catch (InvalidManagedModelElementTypeException e) {
            assert e.message.startsWith("Invalid managed model type ${getName(errorType)}: ")
            assert e.message =~ msgPattern
        }
    }

    private ModelSchema<?> extract(ModelType<?> modelType) {
        extractor.extract(modelType, new ModelSchemaCache())
    }

    private ModelSchema<?> extract(Class<?> clazz) {
        extract(ModelType.of(clazz))
    }

    private String getName(ModelType<?> modelType) {
        modelType
    }

    private String getName(Class<?> clazz) {
        clazz.name
    }
}
