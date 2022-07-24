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

import org.gradle.model.Managed
import org.gradle.model.ModelMap
import org.gradle.model.Unmanaged
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.ModelTypeInitializationException
import org.gradle.model.internal.core.NodeInitializerRegistry
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.binding.StructBindingsStore
import org.gradle.model.internal.manage.schema.ModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ScalarTypes
import org.gradle.util.internal.TextUtil

import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class ManagedModelInitializerTest extends ProjectRegistrySpec {

    def classLoader = new GroovyClassLoader(getClass().classLoader)
    static final List<Class<?>> JDK_SCALAR_TYPES = ScalarTypes.TYPES.rawClass

    @Override
    protected NodeInitializerRegistry createNodeInitializerRegistry(ModelSchemaStore schemaStore, StructBindingsStore structBindingsStore) {
        // Not shared across tests as test may add constructible types only applying to that particular test
        return new DefaultNodeInitializerRegistry(schemaStore, structBindingsStore)
    }

    def "should fail with a contextual exception for managed collections properties"() {
        when:
        realizeNodeOfType(ManagedWithInvalidModelMap)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '${fullyQualifiedNameOf(ManagedWithInvalidModelMap)}' can not be constructed.
Its property 'org.gradle.model.ModelMap<java.io.FileInputStream> map' is not a valid managed collection
A managed collection can not contain 'java.io.FileInputStream's
A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:
        - A managed type (annotated with @Managed)""")
    }

    @Managed
    interface ManagedWithInvalidModelMap {
        ModelMap<FileInputStream> getMap()
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
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '${fullyQualifiedNameOf(ManagedWithInvalidScalarCollection)}' can not be constructed.
Its property 'java.util.List<java.io.FileInputStream> scalarThings' is not a valid scalar collection
A scalar collection can not contain 'java.io.FileInputStream's
A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)""")
    }

    @Managed
    interface ManagedWithInvalidScalarCollection {
        List<FileInputStream> getScalarThings()
    }

    def "should fail with a contextual exception for a managed type with an invalid readonly property"() {
        when:
        realizeNodeOfType(ManagedReadOnlyWithInvalidProperty)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '${fullyQualifiedNameOf(ManagedReadOnlyWithInvalidProperty)}' can not be constructed.
Its property 'java.io.FileInputStream stream' can not be constructed
It must be one of:
    - A managed type (annotated with @Managed)
    - A managed collection. A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:
        - A managed type (annotated with @Managed)
    - A scalar collection. A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)
    - An unmanaged property (i.e. annotated with @Unmanaged)""")
    }

    @Managed
    interface ManagedReadOnlyWithInvalidProperty {
        FileInputStream getStream()
    }

    def "should fail with a contextual exception for a managed type with an invalid readwrite property"() {
        when:
        realizeNodeOfType(ManagedReadWriteWithInvalidProperty)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause.message == TextUtil.toPlatformLineSeparators("""A model element of type: '${fullyQualifiedNameOf(ManagedReadWriteWithInvalidProperty)}' can not be constructed.
Its property 'java.io.FileInputStream stream' can not be constructed
It must be one of:
    - A managed type (annotated with @Managed)
    - A managed collection. A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:
        - A managed type (annotated with @Managed)
    - A scalar collection. A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (String, Boolean, Character, Byte, Short, Integer, Float, Long, Double, BigInteger, BigDecimal, File)
    - An unmanaged property (i.e. annotated with @Unmanaged)""")
    }

    @Managed
    interface ManagedReadWriteWithInvalidProperty {
        FileInputStream getStream()

        void setStream(FileInputStream stream)
    }

    def "should fail with a reasonable exception when a type is not managed and not constructible"() {
        when:
        realizeNodeOfType(NonManaged)

        then:
        def ex = thrown(ModelTypeInitializationException)
        ex.message == TextUtil.toPlatformLineSeparators("""A model element of type: '$NonManaged.name' can not be constructed.
It must be one of:
    - A managed type (annotated with @Managed)""")
    }

    def "should not fail with an exception for a managed type with an invalid readwrite but unmanaged property"() {
        expect:
        realizeNodeOfType(ManagedReadWriteWithInvalidUnmanagedProperty)
    }

    @Managed
    interface ManagedReadWriteWithInvalidUnmanagedProperty {
        @Unmanaged
        FileInputStream getStream()

        void setStream(FileInputStream stream)
    }

    def "must be symmetrical"() {
        expect:
        failWhenRealized OnlyGetter, "read only property 'name' has non managed type java.lang.String, only managed types can be used"
    }

    def "only selected unmanaged property types are allowed #type"() {
        expect:
        failWhenRealized(type,
            canNotBeConstructed(fullyQualifiedNameOf(type)),
            "Its property '${fullyQualifiedNameOf(failingProperty)} $propertyName' can not be constructed",
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
            canNotBeConstructed(fullyQualifiedNameOf(MissingUnmanaged)),
            "Its property 'java.io.InputStream thing' can not be constructed",
            "It must be one of:",
            "- An unmanaged property (i.e. annotated with @Unmanaged)"
        )
    }

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

    def "must have a setter - #managedType.simpleName"() {
        expect:
        failWhenRealized(managedType, "Invalid managed model type '${fullyQualifiedNameOf(managedType)}': read only property 'thing' has non managed type boolean, only managed types can be used")

        where:
        managedType << [OnlyIsGetter, OnlyGetGetter]
    }

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
            "Its property '${collectionType.name}<java.lang.String> items' can not be constructed")
        where:
        collectionType << [LinkedList, ArrayList, SortedSet, TreeSet]
    }


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

    def "throws an error if we use unsupported type #innerType.simpleName as element type of a scalar collection"() {
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
            "Its property 'java.util.List<${innerType.name}> items' is not a valid scalar collection",
            "A scalar collection can not contain '${innerType.name}'s")

        where:
        innerType << [Date, AtomicInteger]
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
        registry.registerWithInitializer("bar", type, nodeInitializerRegistry)
        registry.realize("bar", type)
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
