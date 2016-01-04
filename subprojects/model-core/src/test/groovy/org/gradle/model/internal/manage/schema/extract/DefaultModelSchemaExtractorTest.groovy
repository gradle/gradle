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

import groovy.transform.NotYetImplemented
import org.gradle.api.Action
import org.gradle.internal.reflect.MethodDescription
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.ModelSet
import org.gradle.model.Unmanaged
import org.gradle.model.internal.manage.schema.*
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

import java.beans.Introspector
import java.util.regex.Pattern

import static org.gradle.model.internal.manage.schema.ModelProperty.StateManagementType.MANAGED
import static org.gradle.model.internal.manage.schema.ModelProperty.StateManagementType.UNMANAGED

@SuppressWarnings("GroovyPointlessBoolean")
class DefaultModelSchemaExtractorTest extends Specification {
    def store = new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies())
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    static final List<Class<? extends Serializable>> JDK_SCALAR_TYPES = ScalarTypes.TYPES.rawClass

    static interface NotAnnotatedInterface {}

    def "unmanaged type"() {
        expect:
        extract(NotAnnotatedInterface) instanceof UnmanagedImplStructSchema
    }

    @Managed
    static class EmptyStaticClass {}

    def "must be interface"() {
        when:
        extract(EmptyStaticClass)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $EmptyStaticClass.name is not a valid model element type:
- Must be defined as an interface or an abstract class."""
    }

    @Managed
    static interface ParameterizedEmptyInterface<T> {}

    def "cannot parameterize"() {
        when:
        extract(ParameterizedEmptyInterface)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $ParameterizedEmptyInterface.name is not a valid model element type:
- Cannot be a parameterized type."""
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
    static interface SingleStringNameProperty {
        String getName()

        void setName(String name)
    }

    def "extract single property"() {
        when:
        def properties = extract(SingleStringNameProperty).properties

        then:
        properties*.name == ["name"]
        properties*.type == [ModelType.of(String)]
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

    interface SetterOnlyUnmanaged {
        void setName(String name);
    }

    def "setter only unmanaged"() {
        when:
        def schema = extract(SetterOnlyUnmanaged)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.getProperty("name") == null
    }

    @Unroll
    def "primitive types are supported - #primitiveType"() {
        when:
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $primitiveType.name getPrimitiveProperty()

                void setPrimitiveProperty($primitiveType.name value)
            }
        """

        def properties = extract(interfaceWithPrimitiveProperty).properties

        then:
        properties*.name == ["primitiveProperty"]
        properties*.type == [ModelType.of(primitiveType)]

        where:
        primitiveType << [
            byte,
            boolean,
            char,
            float,
            long,
            short,
            int,
            double]
    }

    @Unroll
    def "Misaligned types #firstType and #secondType"() {
        when:
        def interfaceWithPrimitiveProperty = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface PrimitiveProperty {
                $firstType.name getPrimitiveProperty()

                void setPrimitiveProperty($secondType.name value)
            }
        """

        then:
        fail(interfaceWithPrimitiveProperty, "(expected: ${firstType.name}, found: ${secondType.name})")

        where:
        firstType | secondType
        byte      | Byte
        boolean   | Boolean
        char      | Character
        float     | Float
        long      | Long
        short     | Short
        int       | Integer
        double    | Double
        Byte      | byte
        Boolean   | boolean
        Character | char
        Float     | float
        Long      | long
        Short     | short
        Integer   | int
        Double    | double
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
        def properties = extract(MultipleProps).properties

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
        extract(SelfReferencing).getProperty("self").type == ModelType.of(SelfReferencing)
    }

    @Managed
    interface HasSingleCharGetter {
        String getA()
        void setA(String a)
    }

    def "allow single char getters"() {
        when:
        def schema = store.getSchema(HasSingleCharGetter)

        then:
        schema instanceof ManagedImplSchema
        def a = schema.properties[0]
        assert a instanceof ModelProperty
        a.name == "a"
    }

    @Managed
    interface HasSingleCharFirstPartGetter {
        String getcCompiler()
        void setcCompiler(String cCompiler)
    }

    @NotYetImplemented
    def "single char first camel-case part getters extraction is javabeans compliant"() {
        when:
        def schema = store.getSchema(HasSingleCharFirstPartGetter)

        then:
        schema instanceof ManagedImplSchema
        def cCompiler = schema.properties[0]
        assert cCompiler instanceof ModelProperty
        cCompiler.name == Introspector.decapitalize('cCompiler')
    }

    @Managed
    interface HasDoubleCapsStartingGetter {
        String getCFlags()
        void setCFlags(String cflags)
    }

    @NotYetImplemented
    def "double uppercase char first getters extraction is javabeans compliant"() {
        when:
        def schema = store.getSchema(HasDoubleCapsStartingGetter)

        then:
        schema instanceof ManagedImplSchema
        def cflags = schema.properties[0]
        assert cflags instanceof ModelProperty
        cflags.name == Introspector.decapitalize('CFlags')
    }

    @Managed
    interface HasFullCapsGetter {
        String getURL()
        void setURL(String url)
    }

    @NotYetImplemented
    def "full caps getters extraction is javabeans compliant"() {
        when:
        def schema = store.getSchema(HasFullCapsGetter)

        then:
        schema instanceof ManagedImplSchema
        def url = schema.properties[0]
        assert url instanceof ModelProperty
        url.name == Introspector.decapitalize('URL')
    }

    @Managed
    interface HasTwoFirstsCharLowercaseGetter {
        String getccCompiler()
        void setccCompiler(String ccCompiler)
    }

    def "reject two firsts char lowercase getters"() {
        expect:
        fail HasTwoFirstsCharLowercaseGetter, "only paired getter/setter methods are supported"
    }

    @Managed
    interface HasGetGetterLikeMethod {
        String gettingStarted()
    }

    def "get-getters-like methods not considered as getters"() {
        expect:
        fail HasGetGetterLikeMethod, "only paired getter/setter methods are supported"
    }

    @Managed
    interface HasIsGetterLikeMethod {
        boolean isidore()
    }

    def "is-getters-like methods not considered as getters"() {
        expect:
        fail HasIsGetterLikeMethod, "only paired getter/setter methods are supported"
    }

    @Managed
    interface HasSetterLikeMethod {
        void settings(String settings)
    }

    def "setters-like methods not considered as setters"() {
        expect:
        fail HasSetterLikeMethod, "only paired getter/setter methods are supported"
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
        extract(type).getProperty("a").type == extract(A1).type
        extract(type).getProperty("b").type == extract(B1).type
        extract(type).getProperty("c").type == extract(C1).type
        extract(type).getProperty("d").type == extract(D1).type

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
        def properties = extract(WithInheritedProperties).properties

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
        def properties = extract(WithMultipleParents).properties

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
        def properties = extract(WithInheritedPropertiesFromGrandparent).properties

        then:
        properties*.name == ["count", "flag", "name"]
    }

    static interface WithInheritedPropertiesFromNotAnnotated extends SinglePropertyNotAnnotated {
        Integer getCount()

        void setCount(Integer count)
    }

    def "can extract inherited properties from an interface not annotated with @Managed"() {
        when:
        def properties = extract(WithInheritedPropertiesFromNotAnnotated).properties

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
        def properties = extract(SamePropertyInMultipleTypes).properties

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
        def properties = extract(WritableProperty).properties

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

    def "type argument of a model set has to be specified"() {
        given:
        def type = ModelType.of(ModelSet.class)

        when:
        extract(type)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $type is not a valid model element type:
- type parameter of $ModelSet.name has to be specified"""
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
        def schema = extract(SpecialModel)

        then:
        schema.properties*.type == [ModelType.of(SpecialThing)]
    }

    @Unroll
    def "type argument of a model set cannot be a wildcard - #type"() {
        when:
        extract(type)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $type is not a valid model element type:
- type parameter of $ModelSet.name cannot be a wildcard"""

        where:
        type << [
            new ModelType<ModelSet<?>>() {},
            new ModelType<ModelSet<? extends A1>>() {},
            new ModelType<ModelSet<? super A1>>() {}
        ]
    }

    def "type argument of a model set has to be a valid managed type"() {
        given:
        def type = new ModelType<ModelSet<SetterOnly>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        def invalidMethodDescription = MethodDescription.name("setName").returns(void.class).owner(SetterOnly).takes(String)
        e.message == """Invalid managed model type $SetterOnly.name: only paired getter/setter methods are supported (invalid methods: ${invalidMethodDescription}).
The type was analyzed due to the following dependencies:
$type
  \\--- element type ($SetterOnly.name)"""
    }

    def "specializations of model set are not supported"() {
        given:
        def type = new ModelType<SpecialModelSet<A1>>() {}

        when:
        extract(type)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $type is not a valid model element type:
- subtyping $ModelSet.name is not supported"""
    }

    def "model sets of model set are supported"() {
        given:
        def type = new ModelType<ModelSet<ModelSet<A1>>>() {}

        expect:
        store.getSchema(type) instanceof ModelSetSchema
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

    static class MyFile extends File {
        MyFile(String s) {
            super(s)
        }
    }

    def "subclasses of non final scalar types are treated as unmanaged"() {
        expect:
        extract(type) instanceof UnmanagedImplStructSchema

        where:
        type << [MyBigInteger, MyBigDecimal, MyFile]
    }

    static enum MyEnum {
        A, B, C
    }

    def "can extract enum"() {
        expect:
        extract(MyEnum) instanceof ScalarValueSchema
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

    private void fail(extractType, String msgPattern) {
        fail(extractType, extractType, msgPattern)
    }

    def "subtype can declare property unmanaged"() {
        expect:
        extract(ExtendsMissingUnmanaged).getProperty("thing").type.rawClass == InputStream
    }

    @Managed
    static interface ExtendsMissingUnmanaged {
        @Unmanaged
        InputStream getThing();

        void setThing(InputStream inputStream);
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
        extract(AddsSetterToNoSetterForUnmanaged).getProperty("thing").type.rawClass == InputStream
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
        when:
        extract(ConstructorWithArguments)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $ConstructorWithArguments.name is not a valid model element type:
- Constructor ConstructorWithArguments(java.lang.String) is not valid: Custom constructors are not supported."""

        when:
        extract(AdditionalConstructorWithArguments)

        then:
        e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $AdditionalConstructorWithArguments.name is not a valid model element type:
- Constructor AdditionalConstructorWithArguments(java.lang.String) is not valid: Custom constructors are not supported."""

        when:
        extract(CustomConstructorInSuperClass)

        then:
        e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $CustomConstructorInSuperClass.name is not a valid model element type:
- Constructor SuperConstructorWithArguments(java.lang.String) is not valid: Custom constructors are not supported."""
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
        when:
        extract(WithInstanceScopedField)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type ${WithInstanceScopedField.name} is not a valid model element type:
- Field name is not valid: Fields must be static final.
- Field age is not valid: Fields must be static final."""

        when:
        extract(WithInstanceScopedFieldInSuperclass)

        then:
        e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type ${WithInstanceScopedFieldInSuperclass.name} is not a valid model element type:
- Field WithInstanceScopedField.name is not valid: Fields must be static final.
- Field WithInstanceScopedField.age is not valid: Fields must be static final."""
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
        when:
        extract(ProtectedAbstractMethods)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type ${ProtectedAbstractMethods.name} is not a valid model element type:
- Method getName() is not a valid rule method: Protected and private methods are not supported.
- Method setName(java.lang.String) is not a valid rule method: Protected and private methods are not supported."""

        when:
        extract(ProtectedAbstractMethodsInSuper)

        then:
        e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type ${ProtectedAbstractMethodsInSuper.name} is not a valid model element type:
- Method ProtectedAbstractMethods.getName() is not a valid rule method: Protected and private methods are not supported.
- Method ProtectedAbstractMethods.setName(java.lang.String) is not a valid rule method: Protected and private methods are not supported."""
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
        when:
        extract(ProtectedAndPrivateNonAbstractMethods)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type ${ProtectedAndPrivateNonAbstractMethods.name} is not a valid model element type:
- Method setName(java.lang.String) is not a valid rule method: Protected and private methods are not supported.
- Method getName() is not a valid rule method: Protected and private methods are not supported."""

        when:
        extract(ProtectedAndPrivateNonAbstractMethodsInSuper)

        then:
        e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type ${ProtectedAndPrivateNonAbstractMethodsInSuper.name} is not a valid model element type:
- Method ProtectedAndPrivateNonAbstractMethods.setName(java.lang.String) is not a valid rule method: Protected and private methods are not supported.
- Method ProtectedAndPrivateNonAbstractMethods.getName() is not a valid rule method: Protected and private methods are not supported."""
    }

    interface SomeMap extends ModelMap<List<String>> {
    }

    def "specialized map"() {
        expect:
        def schema = extract(SomeMap)
        assert schema instanceof SpecializedMapSchema
        schema.elementType == new ModelType<List<String>>() {}
        schema.implementationType
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

    @Unroll
    def "can extract a simple managed type with a property of #type"() {
        when:
        Class<?> generatedClass = managedClass(type)

        then:
        extract(generatedClass)

        where:
        type << JDK_SCALAR_TYPES
    }

    Class<?> managedClass(Class<?> type) {
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

    static class CustomThing {}

    static class UnmanagedThing {}

    def "can register custom strategy"() {
        when:
        def strategy = Mock(ModelSchemaExtractionStrategy) {
            extract(_) >> { ModelSchemaExtractionContext extractionContext ->
                if (extractionContext.type.rawClass == CustomThing) {
                    extractionContext.found(new ScalarValueSchema<CustomThing>(extractionContext.type))
                }
            }
        }
        def extractor = new DefaultModelSchemaExtractor([strategy])
        def store = new DefaultModelSchemaStore(extractor)

        then:
        store.getSchema(CustomThing) instanceof ScalarValueSchema
    }

    def "custom strategy can register dependency on other type"() {
        def strategy = Mock(ModelSchemaExtractionStrategy)
        def extractor = new DefaultModelSchemaExtractor([strategy])
        def store = new DefaultModelSchemaStore(extractor)

        when:
        def customSchema = store.getSchema(CustomThing)

        then:
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(CustomThing)
            extractionContext.child(ModelType.of(UnmanagedThing), "child")
            extractionContext.found(new ScalarValueSchema<CustomThing>(extractionContext.type))
        }
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(UnmanagedThing)
            extractionContext.found(new ScalarValueSchema<UnmanagedThing>(extractionContext.type))
        }

        and:
        customSchema instanceof ScalarValueSchema
    }

    def "validator is invoked after all dependencies have been visited"() {
        def strategy = Mock(ModelSchemaExtractionStrategy)
        def validator = Mock(Action)
        def extractor = new DefaultModelSchemaExtractor([strategy])
        def store = new DefaultModelSchemaStore(extractor)

        when:
        store.getSchema(CustomThing)

        then:
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(CustomThing)
            extractionContext.addValidator(validator)
            extractionContext.child(ModelType.of(UnmanagedThing), "child")
            extractionContext.found(new ScalarValueSchema<CustomThing>(extractionContext.type))
        }
        1 * strategy.extract(_) >> { ModelSchemaExtractionContext extractionContext ->
            assert extractionContext.type == ModelType.of(UnmanagedThing)
            extractionContext.found(new ScalarValueSchema<UnmanagedThing>(extractionContext.type))
        }

        then:
        1 * validator.execute(_)
    }

    def "model map type doesn't have to be managed type in an unmanaged type"() {
        expect:
        extract(UnmanagedModelMapInUnmanagedType).getProperty("things").type.rawClass == ModelMap
    }

    static abstract class UnmanagedModelMapInUnmanagedType {
        ModelMap<InputStream> getThings() { null }
    }

    static class SimpleUnmanagedType {
        String prop

        String getCalculatedProp() {
            "calc"
        }
    }

    def "can retrieve property value"() {
        def instance = new SimpleUnmanagedType(prop: "12")

        when:
        def schema = extract(SimpleUnmanagedType)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.getProperty("prop").getPropertyValue(instance) == "12"
        schema.getProperty("calculatedProp").getPropertyValue(instance) == "calc"
    }

    static abstract class SimpleUnmanagedTypeWithAnnotations {
        @CustomTestAnnotation("unmanaged")
        abstract String getUnmanagedProp()

        @CustomTestAnnotation("unmanagedSetter")
        abstract void setUnmanagedProp(String value)

        @CustomTestAnnotation("unmanagedCalculated")
        String getUnmanagedCalculatedProp() {
            return "unmanaged-calculated"
        }

        boolean isBuildable() { true }

        int getTime() { 0 }
    }

    def "properties are extracted from unmanaged type"() {
        when:
        def schema = extract(SimpleUnmanagedTypeWithAnnotations)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.properties*.name == ["buildable", "time", "unmanagedCalculatedProp", "unmanagedProp"]

        schema.getProperty("unmanagedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedProp").isWritable() == true
        schema.getProperty("unmanagedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedCalculatedProp").isWritable() == false
        schema.getProperty("buildable").stateManagementType == UNMANAGED
        schema.getProperty("buildable").isWritable() == false
        schema.getProperty("time").stateManagementType == UNMANAGED
        schema.getProperty("time").isWritable() == false
    }

    static interface UnmanagedSuperType {
        @CustomTestAnnotation("unmanaged")
        abstract String getUnmanagedProp()

        @CustomTestAnnotation("unmanagedSetter")
        abstract void setUnmanagedProp(String value)

        @CustomTestAnnotation("unmanagedCalculated")
        String getUnmanagedCalculatedProp()

        boolean isBuildable()

        int getTime()
    }

    @Managed
    static abstract class ManagedTypeWithAnnotationsExtendingUnmanagedType implements UnmanagedSuperType {
        @CustomTestAnnotation("managed")
        abstract String getManagedProp()

        @CustomTestAnnotation("managedSetter")
        abstract void setManagedProp(String managedProp)

        @CustomTestAnnotation("managedCalculated")
        String getManagedCalculatedProp() {
            return "calc"
        }
    }

    def "properties are extracted from unmanaged type with managed super-type"() {
        def extractor = DefaultModelSchemaExtractor.withDefaultStrategies()
        def store = new DefaultModelSchemaStore(extractor)

        when:
        def schema = store.getSchema(ManagedTypeWithAnnotationsExtendingUnmanagedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["buildable", "managedCalculatedProp", "managedProp", "time", "unmanagedCalculatedProp", "unmanagedProp"]

        schema.getProperty("unmanagedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedProp").isWritable() == true

        schema.getProperty("unmanagedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("unmanagedCalculatedProp").isWritable() == false

        schema.getProperty("managedProp").stateManagementType == MANAGED
        schema.getProperty("managedProp").isWritable() == true

        schema.getProperty("managedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("managedCalculatedProp").isWritable() == false

        schema.getProperty("buildable").stateManagementType == UNMANAGED
        schema.getProperty("buildable").isWritable() == false

        schema.getProperty("time").stateManagementType == UNMANAGED
        schema.getProperty("time").isWritable() == false
    }

    @Managed
    static abstract class SimplePurelyManagedType {
        @CustomTestAnnotation("managed")
        abstract String getManagedProp()

        @CustomTestAnnotation("managedSetter")
        abstract void setManagedProp(String managedProp)

        @CustomTestAnnotation("managedCalculated")
        String getManagedCalculatedProp() {
            return "calc"
        }
    }

    def "properties are extracted from managed type"() {
        when:
        def schema = extract(SimplePurelyManagedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["managedCalculatedProp", "managedProp"]

        schema.getProperty("managedProp").stateManagementType == MANAGED
        schema.getProperty("managedProp").isWritable() == true

        schema.getProperty("managedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("managedCalculatedProp").isWritable() == false
    }

    @Managed
    static abstract class OverridingManagedSubtype extends SimplePurelyManagedType {
        @Override
        @CustomTestAnnotation("overriddenManaged")
        @CustomTestAnnotation2
        abstract String getManagedProp()

        @Override
        @CustomTestAnnotation2
        String getManagedCalculatedProp() { return "overridden " }
    }

    def "property annotations when overridden retain most significant value"() {
        when:
        def schema = extract(OverridingManagedSubtype)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["managedCalculatedProp", "managedProp"]

        schema.getProperty("managedProp").stateManagementType == MANAGED
        schema.getProperty("managedProp").isWritable() == true

        schema.getProperty("managedCalculatedProp").stateManagementType == UNMANAGED
        schema.getProperty("managedCalculatedProp").isWritable() == false
    }

    class MyAspect implements ModelSchemaAspect {}

    @Managed
    static abstract class MyTypeOfAspect {
        abstract String getProp()

        abstract void setProp(String prop)

        String getCalculatedProp() {
            return "calc"
        }
    }

    def "aspects can be extracted"() {
        def aspect = new MyAspect()
        def aspectExtractionStrategy = Mock(ModelSchemaAspectExtractionStrategy)
        def extractor = DefaultModelSchemaExtractor.withDefaultStrategies([], new ModelSchemaAspectExtractor([aspectExtractionStrategy]))
        def store = new DefaultModelSchemaStore(extractor)

        when:
        def resultSchema = store.getSchema(MyTypeOfAspect)

        then:
        assert resultSchema instanceof StructSchema
        resultSchema.hasAspect(MyAspect)
        resultSchema.getAspect(MyAspect) == aspect

        1 * aspectExtractionStrategy.extract(_, _) >> { ModelSchemaExtractionContext<?> extractionContext, List<ModelPropertyExtractionResult> propertyResults ->
            assert propertyResults*.property*.name == ["calculatedProp", "prop"]
            return new ModelSchemaAspectExtractionResult(aspect)
        }
        0 * _
    }

    @Managed
    interface HasIsTypeGetter {
        boolean isRedundant()

        void setRedundant(boolean redundant)
    }

    @Managed
    interface HasGetTypeGetter {
        boolean getRedundant()

        void setRedundant(boolean redundant)
    }

    def "supports a boolean property with a get style getter"() {
        when:
        store.getSchema(ModelType.of(HasGetTypeGetter))

        then:
        noExceptionThrown()
    }

    @Managed
    interface HasDualGetter {
        boolean isRedundant()

        boolean getRedundant()

        void setRedundant(boolean redundant)
    }

    def "allows both is and get style getters"() {
        when:
        def schema = store.getSchema(HasDualGetter)

        then:
        schema instanceof ManagedImplSchema
        def redundant = schema.properties[0]
        assert redundant instanceof ModelProperty
        redundant.getAccessor(PropertyAccessorType.IS_GETTER) != null
        redundant.getAccessor(PropertyAccessorType.GET_GETTER) != null
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBoolean {
        String isThing()

        void setThing(String thing)
    }

    @Managed
    interface IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean {
        Boolean isThing()

        void setThing(Boolean thing)
    }

    def "supports a boolean property with an is style getter"() {
        expect:
        store.getSchema(ModelType.of(HasIsTypeGetter))
    }

    @Unroll
    def "should not allow 'is' as a prefix for getter on non primitive boolean"() {
        when:
        store.getSchema(IsNotAllowedForOtherTypeThanBoolean)

        then:
        def ex = thrown(InvalidManagedModelElementTypeException)
        ex.message =~ /getter method name must start with 'get'/

        where:
        managedType << [IsNotAllowedForOtherTypeThanBoolean, IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean]
    }

    abstract class HasStaticProperties {
        static String staticValue
        String value
    }

    def "does not extract static properties"() {
        def schema = store.getSchema(HasStaticProperties)
        expect:
        schema.properties*.name == ["value"]
    }

    abstract class HasProtectedAndPrivateProperties {
        String value
        protected String protectedValue
        private String privateValue
    }

    def "does not extract protected and private properties"() {
        def schema = store.getSchema(HasProtectedAndPrivateProperties)
        expect:
        schema.properties*.name == ["value"]
    }

    @Managed
    interface HasIsAndGetPropertyWithDifferentTypes {
        boolean isValue()

        String getValue()
    }

    def "handles is/get property with non-matching type"() {
        when:
        store.getSchema(HasIsAndGetPropertyWithDifferentTypes)

        then:
        def ex = thrown InvalidManagedModelElementTypeException
        ex.message.contains "property 'value' has both 'isValue()' and 'getValue()' getters, but they don't both return a boolean"
    }

    @Unroll
    def "supports read-only List<#type.simpleName> property"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<${type.simpleName}> getItems()
            }
        """

        def schema = extract(managedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["items"]

        schema.getProperty("items").stateManagementType == MANAGED
        schema.getProperty("items").isWritable() == false

        where:
        type << JDK_SCALAR_TYPES
    }

    @Unroll
    def "read-write List<#type.simpleName> property is allowed"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<${type.simpleName}> getItems()
                void setItems(List<${type.simpleName}> items)
            }
        """

        def schema = extract(managedType)

        then:
        assert schema instanceof ManagedImplStructSchema
        schema.properties*.name == ["items"]

        schema.getProperty("items").stateManagementType == MANAGED
        schema.getProperty("items").isWritable() == true

        where:
        type << JDK_SCALAR_TYPES
    }

    def "displays a reasonable error message when getter and setter of a property of collection of scalar types do not use the same generic type"() {
        given:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<String> getItems()
                void setItems(List<Integer> integers)
            }
        """

        when:
        extract(managedType)

        then:
        InvalidManagedModelElementTypeException ex = thrown()
        ex.message.contains 'setter method param must be of exactly the same type as the getter returns (expected: java.util.List<java.lang.String>, found: java.util.List<java.lang.Integer>)'
    }

    @Unroll
    def "should not throw an error if we use unsupported collection type #collectionType.simpleName on a non-managed type"() {
        given:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            interface CollectionType {
                ${collectionType.name}<String> getItems()
            }
        """

        when:
        def schema = extract(managedType)

        then:
        assert schema instanceof UnmanagedImplStructSchema
        schema.properties*.name == ["items"]

        schema.getProperty("items").stateManagementType == UNMANAGED
        schema.getProperty("items").isWritable() == false

        where:
        collectionType << [LinkedList, ArrayList, SortedSet, TreeSet]
    }


    def "can extract overloaded property from unmanaged struct type"() {
        def unmanagedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            interface UnmanagedWithOverload {
                Integer getThing()
                Integer getThing(int parameter)

                boolean isNice()
                boolean getNice()
                boolean getNice(int parameter)
                boolean isNice(int parameter)
                void setNice(boolean parameter)
            }
        """

        when:
        def schema = extract(unmanagedType)
        assert schema instanceof StructSchema

        then:
        schema.properties*.name == ["nice", "thing"]
        schema.properties*.type*.rawClass == [boolean, Integer]
    }

    interface UnmanagedSuperTypeWithMethod {
        InputStream doSomething(Object param)
    }

    @Managed
    interface ManagedTypeWithOverriddenMethodExtendingUnmanagedTypeWithMethod extends UnmanagedSuperTypeWithMethod {
        @Override InputStream doSomething(Object param)
    }

    def "accept non-property methods from unmanaged supertype overridden in managed type"() {
        expect:
        extract(ManagedTypeWithOverriddenMethodExtendingUnmanagedTypeWithMethod) instanceof ManagedImplStructSchema
    }

    @Managed
    interface ManagedTypeWithCovarianceOverriddenMethodExtendingUnamangedTypeWithMethod extends UnmanagedSuperTypeWithMethod {
        @Override ByteArrayInputStream doSomething(Object param)
    }

    def "accept non-property methods from unmanaged supertype with covariance overridden in managed type"() {
        expect:
        extract(ManagedTypeWithCovarianceOverriddenMethodExtendingUnamangedTypeWithMethod) instanceof ManagedImplStructSchema
    }

    interface UnmanagedSuperTypeWithOverloadedMethod {
        InputStream doSomething(Object param)
        InputStream doSomething(Object param, Object other)
    }

    @Managed
    interface ManagedTypeExtendingUnmanagedTypeWithOverloadedMethod extends UnmanagedSuperTypeWithOverloadedMethod {
        @Override ByteArrayInputStream doSomething(Object param)
        @Override ByteArrayInputStream doSomething(Object param, Object other)
    }

    def "accept non-property overloaded methods from unmanaged supertype overridden in managed type"() {
        expect:
        extract(ManagedTypeExtendingUnmanagedTypeWithOverloadedMethod) instanceof ManagedImplStructSchema
    }

    @Managed
    interface ManagedSuperTypeWithMethod {
        InputStream doSomething(Object param)
    }

    @Managed
    interface ManagedTypeWithOverriddenMethodExtendingManagedTypeWithMethod extends ManagedSuperTypeWithMethod {
        @Override InputStream doSomething(Object param)
    }

    def "reject non-property methods from managed supertype overridden in managed type"() {
        expect:
        fail ManagedTypeWithOverriddenMethodExtendingManagedTypeWithMethod, "overridden methods not supported"
    }

    @Managed
    interface ManagedTypeWithCovarianceOverriddenMethodExtendingMangedTypeWithMethod extends ManagedSuperTypeWithMethod {
        @Override ByteArrayInputStream doSomething(Object param)
    }

    def "reject non-property methods from managed supertype with covariance overridden in managed type"() {
        expect:
        fail ManagedTypeWithCovarianceOverriddenMethodExtendingMangedTypeWithMethod, "overridden methods not supported"
    }

    @Managed
    interface ManagedSuperTypeWithOverloadedMethod {
        InputStream doSomething(Object param)
        InputStream doSomething(Object param, Object other)
    }

    @Managed
    interface ManagedTypeExtendingManagedTypeWithOverloadedMethod extends ManagedSuperTypeWithOverloadedMethod {
        @Override ByteArrayInputStream doSomething(Object param)
        @Override ByteArrayInputStream doSomething(Object param, Object other)
    }

    def "reject overloaded non-property methods from managed supertype overridden in managed type"() {
        expect:
        fail ManagedTypeExtendingManagedTypeWithOverloadedMethod, "overridden methods not supported"
    }

    static abstract class MultipleProblemsSuper {
        private String field1

        MultipleProblemsSuper(String s) {
        }
        private String getPrivate() { field1 }
    }

    @Managed
    static class MultipleProblems<T extends List<?>> extends MultipleProblemsSuper {
        private String field2

        MultipleProblems(String s) {
            super(s)
        }
    }

    def "collects all problems for a type"() {
        when:
        extract(MultipleProblems)

        then:
        def e = thrown(InvalidManagedModelElementTypeException)
        e.message == """Type $MultipleProblems.name is not a valid model element type:
- Must be defined as an interface or an abstract class.
- Cannot be a parameterized type.
- Constructor MultipleProblems(java.lang.String) is not valid: Custom constructors are not supported.
- Field field2 is not valid: Fields must be static final.
- Constructor MultipleProblemsSuper(java.lang.String) is not valid: Custom constructors are not supported.
- Field MultipleProblemsSuper.field1 is not valid: Fields must be static final.
- Method MultipleProblemsSuper.getPrivate() is not a valid rule method: Protected and private methods are not supported."""
    }

    String getName(ModelType<?> modelType) {
        modelType
    }

    String getName(Class<?> clazz) {
        clazz.name
    }
}
