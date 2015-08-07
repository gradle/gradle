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
import org.gradle.internal.reflect.MethodDescription
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.ModelSet
import org.gradle.model.Unmanaged
import org.gradle.model.internal.manage.schema.ModelMapSchema
import org.gradle.model.internal.manage.schema.ModelSchema
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache
import org.gradle.model.internal.type.ModelType
import org.gradle.util.TextUtil
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

class ModelSchemaExtractorTest extends Specification {

    def classLoader = new GroovyClassLoader(getClass().classLoader)
    @Shared
    def extractor = new ModelSchemaExtractor()
    @Shared
    def store = new DefaultModelSchemaStore(extractor)

    static interface NotAnnotatedInterface {}

    def "unmanaged type"() {
        expect:
        extract(NotAnnotatedInterface).kind == ModelSchema.Kind.UNMANAGED
    }

    @Managed
    static class EmptyStaticClass {}

    def "must be interface"() {
        expect:
        fail EmptyStaticClass, "must be defined as an interface or an abstract class"
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
        fail NoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").returns(void.class).owner(NoGettersOrSetters).takes(String)})")
        fail HasExtraNonPropertyMethods, Pattern.quote("nly paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").returns(void.class).owner(HasExtraNonPropertyMethods).takes(String)})")
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
        fail type, Pattern.quote("an unmanaged type")

        where:
        type << [NonStringProperty, ClassWithExtendedFileType]
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
        char.class    | Character
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

    @Managed
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

    @Managed
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
            MethodDescription.name("getValue").owner(SingleFloatValueProperty).returns(Float).takes(),
            MethodDescription.name("getValue").owner(SingleIntegerValueProperty).returns(Integer).takes(),
            MethodDescription.name("getValue").owner(SingleStringValueProperty).returns(String).takes(),
        ]
        def message = Pattern.quote("overloaded methods are not supported (invalid methods: ${invalidMethods.join(", ")})")

        expect:
        fail ConflictingPropertiesInParents, message
    }

    @Managed
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

    @Managed
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
        fail ChildWithNoGettersOrSetters, Pattern.quote("only paired getter/setter methods are supported (invalid methods: ${MethodDescription.name("foo").returns(void.class).owner(NoGettersOrSetters).takes(String)})")
    }

    def "type argument of a managed set has to be specified"() {
        given:
        def type = ModelType.returnType(TypeHolder.getDeclaredMethod("noParam"))

        expect:
        fail type, "type parameter of $ModelSet.name has to be specified"
    }

    static interface TypeHolder {
        ModelSet noParam();
    }

    @Managed
    interface Thing {}

    @Managed
    interface SpecialThing extends Thing {}

    @Managed
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
        fail type, "type parameter of $ModelSet.name cannot be a wildcard"

        where:
        type << [
            new ModelType<ModelSet<?>>() {},
            new ModelType<ModelSet<? extends A1>>() {},
            new ModelType<ModelSet<? super A1>>() {}
        ]
    }

    def "type argument of a managed set has to be managed"() {
        given:
        def type = new ModelType<ModelSet<Object>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == "Invalid managed model type ${new ModelType<ModelSet<Object>>() {}}: cannot create a managed set of type $Object.name as it is an unmanaged type. Only @Managed types are allowed."
    }

    def "type argument of a managed set has to be a valid managed type"() {
        given:
        def type = new ModelType<ModelSet<SetterOnly>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        def invalidMethodDescription = MethodDescription.name("setName").returns(void.class).owner(SetterOnly).takes(String)
        e.message == TextUtil.toPlatformLineSeparators("""Invalid managed model type $SetterOnly.name: only paired getter/setter methods are supported (invalid methods: ${invalidMethodDescription}).
The type was analyzed due to the following dependencies:
$type
  \\--- element type ($SetterOnly.name)""")
    }

    def "specializations of managed set are not supported"() {
        given:
        def type = new ModelType<SpecialModelSet<A1>>() {}

        expect:
        fail type, "subtyping $ModelSet.name is not supported"
    }

    def "managed sets of managed set are not supported"() {
        given:
        def type = new ModelType<ModelSet<ModelSet<A1>>>() {}

        expect:
        fail type, "$ModelSet.name cannot be used as type parameter of $ModelSet.name"
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

    def "unmanaged types must be annotated with unmanaged"() {
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

    @Managed
    static abstract class NonAbstractGetterWithSetter {
        String getName() {}

        abstract void setName(String name)
    }

    @Managed
    static abstract class NonAbstractSetter {
        abstract String getName()

        void setName(String name) {}
    }

    def "non-abstract mutator methods are not allowed"() {
        expect:
        fail NonAbstractGetterWithSetter, Pattern.quote("setters are not allowed for non-abstract getters (invalid method: ${MethodDescription.name("setName").owner(NonAbstractGetterWithSetter).returns(void.class).takes(String)})")
        fail NonAbstractSetter, Pattern.quote("non-abstract setters are not allowed (invalid method: ${MethodDescription.name("setName").owner(NonAbstractSetter).takes(String).returns(void.class)})")
    }

    @Managed
    static abstract class ConstructorWithArguments {
        ConstructorWithArguments(String arg) {}
    }

    @Managed
    static abstract class AdditionalConstructorWithArguments {
        AdditionalConstructorWithArguments() {}

        AdditionalConstructorWithArguments(String arg) {}
    }

    static class SuperConstructorWithArguments {
        SuperConstructorWithArguments(String arg) {}
    }

    @Managed
    static abstract class ConstructorCallingSuperConstructorWithArgs extends SuperConstructorWithArguments {
        ConstructorCallingSuperConstructorWithArgs() {
            super("foo")
        }
    }

    @Managed
    static abstract class CustomConstructorInSuperClass extends ConstructorCallingSuperConstructorWithArgs {
    }

    def "custom constructors are not allowed"() {
        expect:
        fail ConstructorWithArguments, Pattern.quote("custom constructors are not allowed (invalid method: ${MethodDescription.name("<init>").owner(ConstructorWithArguments).takes(String)})")
        fail AdditionalConstructorWithArguments, Pattern.quote("custom constructors are not allowed (invalid method: ${MethodDescription.name("<init>").owner(AdditionalConstructorWithArguments).takes(String)})")
        fail CustomConstructorInSuperClass, Pattern.quote("custom constructors are not allowed (invalid method: ${MethodDescription.name("<init>").owner(SuperConstructorWithArguments).takes(String)})")
    }

    @Managed
    static abstract class WithInstanceScopedField {
        private String name
        private int age
    }

    @Managed
    static abstract class WithInstanceScopedFieldInSuperclass extends WithInstanceScopedField {
    }

    def "instance scoped fields are not allowed"() {
        expect:
        fail WithInstanceScopedField, Pattern.quote("instance scoped fields are not allowed (found fields: private int ${WithInstanceScopedField.name}.age, private java.lang.String ${WithInstanceScopedField.name}.name)")
        fail WithInstanceScopedFieldInSuperclass, Pattern.quote("instance scoped fields are not allowed (found fields: private int ${WithInstanceScopedField.name}.age, private java.lang.String ${WithInstanceScopedField.name}.name)")
    }

    @Managed
    static abstract class ThrowsInConstructor {
        ThrowsInConstructor() {
            throw new RuntimeException("from constructor")
        }
    }

    def "classes that cannot be instantiated are detected as soon as they are extracted"() {
        when:
        extract(ThrowsInConstructor)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == "Invalid managed model type ${ThrowsInConstructor.name}: instance creation failed"
        e.cause.message == "from constructor"
    }

    @Managed
    static abstract class CallsSetterInConstructor {
        abstract String getName()

        abstract void setName(String name)

        CallsSetterInConstructor() {
            name = "foo"
        }
    }

    def "calling setters from constructor is not allowed"() {
        when:
        extract(CallsSetterInConstructor)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == "Invalid managed model type ${CallsSetterInConstructor.name}: instance creation failed"
        e.cause.class == UnsupportedOperationException
        e.cause.message == "Calling setters of a managed type on itself is not allowed"
    }

    @Managed
    static abstract class ProtectedAbstractMethods {
        protected abstract String getName()

        protected abstract void setName(String name)
    }

    @Managed
    static abstract class ProtectedAbstractMethodsInSuper extends ProtectedAbstractMethods {
    }

    def "protected abstract methods are not allowed"() {
        given:
        def getterDescription = MethodDescription.name("getName").owner(ProtectedAbstractMethods).takes().returns(String)
        def setterDescription = MethodDescription.name("setName").owner(ProtectedAbstractMethods).returns(void.class).takes(String)

        expect:
        fail ProtectedAbstractMethods, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
        fail ProtectedAbstractMethodsInSuper, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
    }

    @Managed
    static abstract class ProtectedAndPrivateNonAbstractMethods {
        protected String getName() {
            return null;
        }

        private void setName(String name) {}
    }

    @Managed
    static abstract class ProtectedAndPrivateNonAbstractMethodsInSuper extends ProtectedAndPrivateNonAbstractMethods {
    }

    def "protected and private non-abstract methods are not allowed"() {
        given:
        def getterDescription = MethodDescription.name("getName").owner(ProtectedAndPrivateNonAbstractMethods).takes().returns(String)
        def setterDescription = MethodDescription.name("setName").owner(ProtectedAndPrivateNonAbstractMethods).returns(void.class).takes(String)

        expect:
        fail ProtectedAndPrivateNonAbstractMethods, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
        fail ProtectedAndPrivateNonAbstractMethodsInSuper, Pattern.quote("protected and private methods are not allowed (invalid methods: $getterDescription, $setterDescription)")
    }

    interface SomeMap extends ModelMap<List<String>> {
    }

    def "specialized map"() {
        expect:
        def schema = extract(SomeMap)
        schema instanceof ModelMapSchema
        schema.elementType == new ModelType<List<String>>() {}
        schema.managedImpl
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
        store.getSchema(modelType)
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

    @Unroll
    def "can extract a simple managed type with a property of #type"() {
        when:
        Class<?> generatedClass = managedClass(type)

        then:
        extract(generatedClass)

        where:
        type << [String, Boolean, Character, Integer, Long, Double, BigInteger, BigDecimal, File]
    }

    @Unroll
    def "should enforce properties of #type are managed"() {
        when:
        Class<?> generatedClass = managedClassWithoutSetter(type)

        then:
        fail generatedClass, "has non managed type ${type.name}, only managed types can be used"

        where:
        type << [String, Boolean, Character, Integer, Long, Double, BigInteger, BigDecimal, File]
    }

    @Managed
    static interface ClassWithExtendedFileType {
        ExtendedFile getExtendedFile()

        void setExtendedFile(ExtendedFile extendedFile)
    }

    static class ExtendedFile extends File {
        ExtendedFile(String pathname) {
            super(pathname)
        }
    }

    private Class<?> managedClass(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
    void set${typeName}(${typeName} a${typeName})
}
""")
    }

    private Class<?> managedClassWithoutSetter(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
}
""")
    }

    static class CustomThing {}

    static class UnmanagedThing {}

    def "can register custom strategy"() {
        when:
        def strategy = Mock(ModelSchemaExtractionStrategy) {
            extract(_, _, _) >> { ModelSchemaExtractionContext extractionContext, ModelSchemaStore store, ModelSchemaCache schemaCache ->
                if (extractionContext.type.rawClass == CustomThing) {
                    return new ModelSchemaExtractionResult(ModelSchema.value(extractionContext.type))
                } else {
                    return null
                }
            }
        }
        def extractor = new ModelSchemaExtractor([strategy])
        def store = new DefaultModelSchemaStore(extractor)

        then:
        store.getSchema(CustomThing).kind == ModelSchema.Kind.VALUE
        store.getSchema(UnmanagedThing).kind == ModelSchema.Kind.UNMANAGED
    }

    @Managed
    static abstract class UnmanagedModelMapInManagedType {
        abstract ModelMap<InputStream> getThings()
    }

    def "model map type must be managed in a managed type"() {
        expect:
        fail UnmanagedModelMapInManagedType, "property 'things' cannot be a model map of type $InputStream.name as it is not a $Managed.name type."
    }

    static abstract class UnmanagedModelMapInUnmanagedType {
        ModelMap<InputStream> getThings() { null }
    }

    def "model map type doesn't have to be managed type in an unmanaged type"() {
        when:
        def schema = extract(UnmanagedModelMapInUnmanagedType)
        println schema.properties

        then:
        schema.properties.get("things").type.rawClass == ModelMap
    }

}
