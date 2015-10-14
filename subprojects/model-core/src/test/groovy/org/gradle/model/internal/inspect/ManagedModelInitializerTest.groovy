/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.inspect
import org.gradle.api.credentials.Credentials
import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.*
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultConstructableTypesRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ScalarTypes
import org.gradle.model.internal.type.ModelType
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ManagedModelInitializerTest extends Specification {

    def store = DefaultModelSchemaStore.instance
    def nodeInitializerRegistry = TestNodeInitializerRegistry.INSTANCE
    def r = new ModelRegistryHelper()
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    static final List<Class<?>> JDK_SCALAR_TYPES = ScalarTypes.TYPES.rawClass

    def setup() {
        r.create(ModelCreators.serviceInstance(DefaultNodeInitializerRegistry.DEFAULT_REFERENCE, nodeInitializerRegistry).build())
    }

    def "should fail with a contextual exception for managed collections properties"() {
        when:
        realizeNodeOfType(ManagedWithInvalidModelMap)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '$ManagedWithInvalidModelMap.name' can not be constructed.
It's property 'org.gradle.model.ModelMap<java.io.FileInputStream> map' is not a valid managed collection
A managed collection can not contain 'java.io.FileInputStream's
A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:
        - A managed type (annotated with @Managed)""")
    }

    @Managed
    interface ManagedWithInvalidModelMap {
        ModelMap<FileInputStream> getMap()
    }

    def "should fail with a contextual exception for a managed model element with an unknown property type"() {
        when:
        def constructableTypesRegistry = new DefaultConstructableTypesRegistry()
        NodeInitializer nodeInitializer = Mock()
        constructableTypesRegistry.registerConstructableType(ModelType.of(Credentials), nodeInitializer)
        nodeInitializerRegistry.registerStrategy(constructableTypesRegistry)
        realizeNodeOfType(ManagedWithUnsupportedType)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '$ManagedWithUnsupportedType.name' can not be constructed.
It's property 'java.io.FileInputStream stream' can not be constructed
It must be one of:
    - A managed collection. A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:
        - A managed type (annotated with @Managed)
        - or a type which Gradle is capable of constructing:
            - org.gradle.api.credentials.Credentials
    - A scalar collection. A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)
    - An unmanaged property (i.e. annotated with @Unmanaged)
    - or a type which Gradle is capable of constructing:
        - org.gradle.api.credentials.Credentials""")
    }

    @Managed
    interface ManagedWithUnsupportedType {
        FileInputStream getStream()
    }

    def "should fail with a contextual exception for scalar collection properties"() {
        when:
        realizeNodeOfType(ManagedWithInvalidScalarCollection)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '$ManagedWithInvalidScalarCollection.name' can not be constructed.
It's property 'java.util.List<java.io.FileInputStream> scalarThings' is not a valid scalar collection
A scalar collection can not contain 'java.io.FileInputStream's
A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)""")
    }

    @Managed
    interface ManagedWithInvalidScalarCollection {
        List<FileInputStream> getScalarThings()
    }

    def "must be symmetrical"() {
        expect:
        failWhenRealized OnlyGetter, "read only property 'name' has non managed type java.lang.String, only managed types can be used"
    }

    @Unroll
    def "only selected unmanaged property types are allowed #type"() {
        expect:
        failWhenRealized(type,
            canNotBeConstructed("${type.name}"),
            "It's property '$failingProperty.name $propertyName' can not be constructed",
            "It must be one of:",
            "    - A managed collection.",
            "    - A scalar collection.",
            "    - An unmanaged property (i.e. annotated with @Unmanaged)"
        )

        where:
        type                      | failingProperty | propertyName
        NonStringProperty         | Object          | 'name'
        ClassWithExtendedFileType | ExtendedFile    | 'extendedFile'
    }

    def "unmanaged types must be annotated with unmanaged"() {
        expect:
        failWhenRealized(MissingUnmanaged,
            canNotBeConstructed(MissingUnmanaged.name),
            "It's property 'java.io.InputStream thing' can not be constructed",
            "It must be one of:",
            "- An unmanaged property (i.e. annotated with @Unmanaged)"
        )
    }

    @Unroll
    def "should enforce properties of #type are managed"() {
        when:
        Class<?> generatedClass = readOnlyManagedClass(type)

        then:
        failWhenRealized generatedClass, "has non managed type ${type.name}, only managed types can be used"

        where:
        type << JDK_SCALAR_TYPES
    }

    Class<?> readOnlyManagedClass(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
}
""")
    }

    Class<?> managedClass(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()`
    void set${typeName}($typeName arg)
}
""")
    }

    @Unroll
    def "must have a setter - #managedType.simpleName"() {
        expect:
        failWhenRealized(managedType, "Invalid managed model type '$managedType.name': read only property 'thing' has non managed type boolean, only managed types can be used")

        where:
        managedType << [OnlyIsGetter, OnlyGetGetter]
    }

    @Unroll
    def "throws an error if we use unsupported collection type #collectionType.simpleName"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                ${collectionType.name}<String> getItems()
            }
        """

        then:
        failWhenRealized(managedType, canNotBeConstructed("CollectionType"),
            "It's property '${collectionType.name}<java.lang.String> items' can not be constructed")
        where:
        collectionType << [LinkedList, ArrayList, SortedSet, TreeSet]
    }


    @Unroll
    def "can initialize a model node with a managed collection property of type #collectionType"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
           import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                ${collectionType.name}<String> getItems()
            }
        """

        then:
        realizeNodeOfType(managedType)

        where:
        collectionType << [Set, List]
    }

    @Unroll
    def "throws an error if we use unsupported type #collectionType.simpleName as element type of a scalar collection"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                List<${innerType.name}> getItems()
            }
        """

        then:
        failWhenRealized(managedType, canNotBeConstructed("CollectionType"),
            "It's property 'java.util.List<${innerType.name}> items' is not a valid scalar collection",
            "A scalar collection can not contain '${innerType.name}'s")

        where:
        innerType << [Date, AtomicInteger]
    }

    def "type of the first argument of void returning model definition has to be @Managed annotated"() {
        expect:
        failWhenRealized(NonManaged,
            canNotBeConstructed("$NonManaged.name"),
            "- An explicitly managed type (i.e. annotated with @Managed)")
    }

    void failWhenRealized(Class type, String... expectedMessages) {
        try {
            realizeNodeOfType(type)
            throw new AssertionError("node realisation of type ${type} should have failed with a cause of:\n$expectedMessages\n")
        }
        catch (ModelRuleExecutionException e) {
            assertExpected(e.cause, expectedMessages)
        } catch (ModelTypeInitializationException e) {
            assertExpected(e, expectedMessages)
        }
    }

    void realizeNodeOfType(Class type) {
        r.create(ModelCreators.of(r.path("bar"), nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forType(ModelType.of(type)))).descriptor(r.desc("bar")).build())
        r.realize("bar", type)
    }

    void assertExpected(Exception e, String... expectedMessages) {
        expectedMessages.each { String error ->
            assert e.message.contains(error)
        }
    }

    String canNotBeConstructed(String type) {
        "A model element of type: '${type}' can not be constructed."
    }

    @Managed
    static interface NonStringProperty {
        Object getName()

        void setName(Object name)
    }

    @Managed
    static interface OnlyGetter {
        String getName()
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

    @Managed
    static interface MissingUnmanaged {
        InputStream getThing();

        void setThing(InputStream inputStream);
    }

    @Managed
    static abstract class UnmanagedModelMapInManagedType {
        abstract ModelMap<InputStream> getThings()
    }

    @Managed
    static interface OnlyGetGetter {
        boolean getThing()
    }

    @Managed
    static interface OnlyIsGetter {
        boolean isThing()
    }
}
