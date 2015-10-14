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
import org.gradle.model.internal.core.*
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.fixture.TestNodeInitializerRegistry
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ScalarTypes
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

class ManagedModelInitializerTest extends Specification {

    def store = DefaultModelSchemaStore.instance
    def nodeInitializerRegistry = TestNodeInitializerRegistry.INSTANCE
    def r = new ModelRegistryHelper()
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    static final List<Class<?>> JDK_SCALAR_TYPES = ScalarTypes.TYPES.rawClass

    def setup() {
        r.create(ModelCreators.serviceInstance(DefaultNodeInitializerRegistry.DEFAULT_REFERENCE, nodeInitializerRegistry).build())
    }

    def "must be symmetrical"() {
        expect:
        failWhenRealized OnlyGetter, "read only property 'name' has non managed type java.lang.String, only managed types can be used"
    }

    @Unroll
    def "only selected unmanaged property types are allowed"() {
        expect:
        failWhenRealized(type,
            canNotBeConstructed("${failingProperty.name}"),
            "It must be one the following:",
            "- A supported scalar type (",
            "- An enumerated type (Enum)",
            "- An explicitly managed type (i.e. annotated with @Managed)",
            "- An explicitly unmanaged property (i.e. annotated with @Unmanaged)",
            "- A scalar collection type ("
        )

        where:
        type                                           | failingProperty
        NonStringProperty         | Object
        ClassWithExtendedFileType | ExtendedFile
    }

    def "unmanaged types must be annotated with unmanaged"() {
        expect:
        failWhenRealized(MissingUnmanaged,
            canNotBeConstructed('java.io.InputStream'),
            "- An explicitly unmanaged property (i.e. annotated with @Unmanaged)")
    }

    @Unroll
    def "should enforce properties of #type are managed"() {
        when:
        Class<?> generatedClass = managedClassWithoutSetter(type)

        then:
        failWhenRealized generatedClass, "has non managed type ${type.name}, only managed types can be used"

        where:
        type << JDK_SCALAR_TYPES
    }

    Class<?> managedClassWithoutSetter(Class<?> type) {
        String typeName = type.getSimpleName()
        return classLoader.parseClass("""
import org.gradle.model.Managed

@Managed
interface Managed${typeName} {
    ${typeName} get${typeName}()
}
""")
    }

    def "model map type must be managed in a managed type"() {
        expect:
        failWhenRealized(UnmanagedModelMapInManagedType,
            canNotBeConstructed(InputStream.name),
            "- A managed collection type (ModelMap<?>, ManagedSet<?>, ModelSet<?>)"
        )
    }

    @Unroll
    def "must have a setter - #managedType.simpleName"() {
        when:
        failWhenRealized(managedType, "Invalid managed model type '$managedType.name': read only property 'thing' has non managed type boolean, only managed types can be used")

        then:
        true

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
        failWhenRealized(managedType, canNotBeConstructed("${collectionType.name}<java.lang.String>"),
            "- A scalar collection type (List, Set)",
            "- A managed collection type (ModelMap<?>, ManagedSet<?>, ModelSet<?>)")

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


    def "type of the first argument of void returning model definition has to be @Managed annotated"() {
        expect:
        failWhenRealized(NonManaged,
            canNotBeConstructed("$NonManaged.name"),
            "- An explicitly managed type (i.e. annotated with @Managed)")
    }

    void failWhenRealized(Class type, String... expectedMessages) {
        try {
            realizeNodeOfType(type)
            throw new AssertionError("node realisation of type $type.name should have failed with a cause of:\n$expectedMessages\n")
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
