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

import org.gradle.api.artifacts.Configuration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ScalarTypesInManagedModelTest extends Specification {

    @Shared
    def store = DefaultModelSchemaStore.getInstance()

    def classloader = new GroovyClassLoader(this.class.classLoader)

    @Unroll
    def "cannot have read only property of scalar type #someType.simpleName"() {
        given:
        def clazz = classloader.parseClass """
            import org.gradle.api.artifacts.Configuration.State
            import org.gradle.model.Managed

            @Managed
            interface ManagedType {
                $someType.canonicalName getManagedProperty()
            }

        """

        when:
        store.getSchema(clazz)

        then:
        def ex = thrown(InvalidManagedModelElementTypeException)
        String expectedMessage = "Invalid managed model type ManagedType: read only property 'managedProperty' has non managed type ${someType.name}, only managed types can be used"
        ex.message == expectedMessage

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
}
