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
import org.gradle.model.internal.ModelValidationTypes
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

class ManagedModelInitializerTest extends Specification implements ModelValidationTypes{

    @Shared
    def store = DefaultModelSchemaStore.getInstance()
    def r = new ModelRegistryHelper()
    def nodeInitializerRegistry = new DefaultNodeInitializerRegistry(store)

    def "must be symmetrical"() {
        expect:
        failWhenRealized ModelValidationTypes.OnlyGetter, "read only property 'name' has non managed type java.lang.String, only managed types can be used"
    }

    def "only selected unmanaged property types are allowed"() {
        expect:
        failWhenRealized type, Pattern.quote("The type must be managed (@Managed) or one of the following types [ModelSet<?>, ManagedSet<?>, ModelMap<?>, List, Set]")

        where:
        type << [ModelValidationTypes.NonStringProperty, ModelValidationTypes.ClassWithExtendedFileType]
    }

    def "unmanaged types must be annotated with unmanaged"() {
        expect:
        failWhenRealizedWithType(ModelValidationTypes.MissingUnmanaged, 'java.io.InputStream')
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

    def "model map type must be managed in a managed type"() {
        expect:
        failWhenRealizedWithType(ModelValidationTypes.UnmanagedModelMapInManagedType, InputStream.name)
    }

    @Unroll
    def "must have a setter - #managedType.simpleName"() {
        when:
        failWhenRealized(managedType, Pattern.quote("Invalid managed model type '$managedType.name': read only property 'thing' has non managed type boolean, only managed types can be used"))

        then:
        true

        where:
        managedType << [ModelValidationTypes.OnlyIsGetter, ModelValidationTypes.OnlyGetGetter]
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
        failWhenRealized(managedType, Pattern.quote("The model node of type: '${collectionType.name}<java.lang.String>' can not be constructed. The type must be managed (@Managed) or one of the following types [ModelSet<?>, ManagedSet<?>, ModelMap<?>, List, Set]"))

        where:
        collectionType << [LinkedList, ArrayList, SortedSet, TreeSet]
    }

    private void failWhenRealized(Class type, String expected) {
        try {
            r.create(ModelCreators.of(r.path("bar"), nodeInitializerRegistry.getNodeInitializer(store.getSchema(type))).descriptor(r.desc("bar")).build())
            r.realize("bar", type)
            throw new AssertionError("node realisation of type ${getName(type)} should have failed with a cause of:\n$expected\n")
        }
        catch (ModelRuleExecutionException e) {
            assert e.cause.message =~ expected
        }
    }

    private void failWhenRealizedWithType(Class type, String failingType) {
        failWhenRealized(type, Pattern.quote("The model node of type: '${failingType}' can not be constructed. The type must be managed (@Managed) or one of the following types [ModelSet<?>, ManagedSet<?>, ModelMap<?>, List, Set]"))
    }
}
