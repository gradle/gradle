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

package org.gradle.model.internal.manage.schema.extract

import groovy.transform.NotYetImplemented
import org.gradle.api.artifacts.Configuration
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.NodeInitializerRegistry
import org.gradle.model.internal.fixture.ModelRegistryHelper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.regex.Pattern

class ScalarTypesInManagedModelTest extends Specification {

    @Shared
    def store = DefaultModelSchemaStore.getInstance()
    def r = new ModelRegistryHelper()
    def nodeInitializerRegistry = new DefaultNodeInitializerRegistry(store)

    def classloader = new GroovyClassLoader(this.class.classLoader)

    def setup() {
        r.create(ModelCreators.bridgedInstance(ModelReference.of(NodeInitializerRegistry), nodeInitializerRegistry).build())
    }

    @Unroll
    def "cannot have read only property of scalar type #someType.simpleName"() {

        when:
        def clazz = classloader.parseClass """
            import org.gradle.api.artifacts.Configuration.State
            import org.gradle.model.Managed

            @Managed
            interface ManagedType {
                $someType.canonicalName getManagedProperty()
            }

        """

        then:
        failWhenRealized(clazz, Pattern.quote("Invalid managed model type 'ManagedType': read only property 'managedProperty' has non managed type ${someType.name}, only managed types can be used") )

        where:
        someType << [
            byte, Byte,
            boolean, Boolean,
            char, Character,
            float, Float,
            long, Long,
            short, Short,
            int, Integer,
            double, Double,
            String,
            BigDecimal,
            BigInteger,
            Configuration.State,
            File]
    }

    @NotYetImplemented
    @Unroll
    def "can have a #type as an @Unmanaged property"() {
        when:
        def clazz = classloader.parseClass """
            import org.gradle.api.artifacts.Configuration.State
            import org.gradle.model.Managed
            import org.gradle.model.Unmanaged

            @Managed
            interface ManagedType {
                @Unmanaged
                $type getUnmanagedReadWriteProperty()

                void setUnmanagedReadWriteProperty($type type)
            }

        """

        then:
        realize(clazz)

        where:
        type << ['List<Date>', 'Set<Date>']

    }

    private void failWhenRealized(Class type, String expected) {
        try {
            realize(type)
            throw new AssertionError("node realisation of type ${type.name} should have failed with a cause of:\n$expected\n")
        }
        catch (ModelRuleExecutionException e) {
            assert e.cause.message =~ expected
        }
    }

    private void realize(Class type) {
        r.create(ModelCreators.of(r.path("bar"), nodeInitializerRegistry.getNodeInitializer(store.getSchema(type))).descriptor(r.desc("bar")).build())
        r.realize("bar", type)
    }
}
