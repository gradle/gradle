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

package org.gradle.model

import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification
import spock.lang.Unroll

class ManagedBooleanGetterTest extends Specification {

    def schemaStore = DefaultModelSchemaStore.getInstance()

    @Managed
    interface Manager {
        boolean isRedundant()

        void setRedundant(boolean redundant)
    }

    def "supports a boolean property with an is style getter"() {
        expect:
        schemaStore.getSchema(ModelType.of(Manager))
    }

    @Managed
    interface IsManager {
        boolean getRedundant()

        void setRedundant(boolean redundant)
    }

    def "supports a boolean property with a get style getter"() {
        expect:
        schemaStore.getSchema(ModelType.of(IsManager))
    }

    @Managed
    interface DualGetterManager {
        boolean isRedundant()

        boolean getRedundant()

        void setRedundant(boolean redundant)
    }

    def "allows both is and get style getters"() {
        expect:
        schemaStore.getSchema(DualGetterManager)
    }

    @Managed
    static interface OnlyGetGetter {
        boolean getThing()
    }

    @Managed
    static interface OnlyIsGetter {
        boolean isThing()
    }

    @Unroll
    def "must have a setter - #managedType.simpleName"() {
        when:
        schemaStore.getSchema(managedType)

        then:
        def ex = thrown(InvalidManagedModelElementTypeException)
        ex.message =~ "read only property 'thing' has non managed type boolean, only managed types can be used"

        where:
        managedType << [OnlyIsGetter, OnlyGetGetter]
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

    @Unroll
    def "should not allow 'is' as a prefix for getter on non primitive boolean"() {
        when:
        schemaStore.getSchema(IsNotAllowedForOtherTypeThanBoolean)

        then:
        def ex = thrown(InvalidManagedModelElementTypeException)
        ex.message =~ /getter method name must start with 'get'/

        where:
        managedType << [IsNotAllowedForOtherTypeThanBoolean, IsNotAllowedForOtherTypeThanBooleanWithBoxedBoolean]
    }
}
