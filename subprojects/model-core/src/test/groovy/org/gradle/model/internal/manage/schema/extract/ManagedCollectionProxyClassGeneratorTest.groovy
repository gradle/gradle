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

import spock.lang.Specification

class ManagedCollectionProxyClassGeneratorTest extends Specification {
    static def generator = new ManagedCollectionProxyClassGenerator()
    static classes = [:]

    def "generates a proxy class for an interface"() {
        def target = Stub(SomeType)
        target.value >> 12

        expect:
        def impl = newInstance(SomeTypeImpl, SpecializedType, target)
        impl instanceof SomeTypeImpl
        impl instanceof SpecializedType
        impl.value == 12
    }

    def "generates a proxy classes for multiple different contract types"() {
        def target = Stub(SomeType)
        target.value >> 12

        expect:
        def impl1 = generate(SomeTypeImpl, SpecializedType)
        def impl2 = generate(SomeTypeImpl, SpecializedType2)
        impl1 != impl2
        SpecializedType.isAssignableFrom(impl1)
        SpecializedType2.isAssignableFrom(impl2)
    }

    SomeType newInstance(Class<? extends SomeType> implType, Class<? extends SomeType> publicType, SomeType target) {
        def generated = generate(implType, publicType)
        return generated.newInstance(target)
    }

    Class<? extends SomeType> generate(Class<? extends SomeType> implType, Class<? extends SomeType> publicType) {
        def generated = classes[publicType]
        if (generated == null) {
            generated = generator.generate(implType, publicType)
            classes[publicType] = generated
        }
        return generated
    }

    interface SomeType {
        Integer getValue()

        void setValue(Integer value)
    }

    interface SpecializedType extends SomeType {}

    interface SpecializedType2 extends SomeType {}

    static class SomeTypeImpl implements SomeType {
        SomeType target

        SomeTypeImpl(SomeType target) {
            this.target = target
        }

        SomeTypeImpl(SomeType target, boolean somePrimitiveType) {
            this.target = target
        }

        @Override
        Integer getValue() {
            return target.value
        }

        @Override
        void setValue(Integer value) {
            target.value = value
        }
    }
}
