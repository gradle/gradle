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

import org.gradle.api.Action
import org.gradle.internal.reflect.PropertyAccessorType
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.ModelSet
import org.gradle.model.Unmanaged
import org.gradle.model.internal.manage.schema.*
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

import java.beans.Introspector

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

    def "extraction of single char first camel-case part getter like getcCompiler() is javabeans compliant"() {
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

    def "extraction of double uppercase char first getter like getCFlags() is javabeans compliant"() {
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

    def "extraction of full caps getter like getURL() is javabeans compliant"() {
        when:
        def schema = store.getSchema(HasFullCapsGetter)

        then:
        schema instanceof ManagedImplSchema
        def url = schema.properties[0]
        assert url instanceof ModelProperty
        url.name == Introspector.decapitalize('URL')
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
        def type = new ModelType<ModelSet<ModelMap>>() {}

        when:
        extract(type)

        then:
        InvalidManagedModelElementTypeException e = thrown()
        e.message == """Type $ModelMap.name is not a valid model element type:
- type parameter of $ModelMap.name has to be specified.

The type was analyzed due to the following dependencies:
$ModelSet.name<$ModelMap.name>
  \\--- element type ($ModelMap.name)"""
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
            extractionContext.child(ModelType.of(UnmanagedThing), "child", validator)
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

        schema.getProperty("unmanagedProp").isWritable() == true
        schema.getProperty("unmanagedCalculatedProp").isWritable() == false
        schema.getProperty("buildable").isWritable() == false
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

        schema.getProperty("unmanagedProp").isWritable() == true
        schema.getProperty("unmanagedCalculatedProp").isWritable() == false
        schema.getProperty("managedProp").isWritable() == true
        schema.getProperty("managedCalculatedProp").isWritable() == false
        schema.getProperty("buildable").isWritable() == false
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

        schema.getProperty("managedProp").isWritable() == true
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

        schema.getProperty("managedProp").isWritable() == true
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

    def "supports a boolean property with an is style getter"() {
        expect:
        store.getSchema(ModelType.of(HasIsTypeGetter))
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

        schema.getProperty("items").isWritable() == false

        where:
        type << JDK_SCALAR_TYPES
    }

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

        schema.getProperty("items").isWritable() == true

        where:
        type << JDK_SCALAR_TYPES
    }

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
    interface ManagedTypeWithCovarianceOverriddenMethodExtendingUnmanagedTypeWithMethod extends UnmanagedSuperTypeWithMethod {
        @Override ByteArrayInputStream doSomething(Object param)
    }

    def "accept non-property methods from unmanaged supertype with covariance overridden in managed type"() {
        expect:
        extract(ManagedTypeWithCovarianceOverriddenMethodExtendingUnmanagedTypeWithMethod) instanceof ManagedImplStructSchema
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

    String getName(ModelType<?> modelType) {
        modelType
    }

    String getName(Class<?> clazz) {
        clazz.name
    }
}
