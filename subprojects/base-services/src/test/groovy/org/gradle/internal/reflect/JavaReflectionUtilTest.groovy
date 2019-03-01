/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect


import org.gradle.testing.internal.util.Specification
import spock.lang.Unroll

import java.lang.reflect.Type

class JavaReflectionUtilTest extends Specification {

    @Unroll
    def "#type has type variable: #hasTypeVariable"() {
        expect:
        JavaReflectionUtil.hasTypeVariable(type) == hasTypeVariable

        where:
        testType << testedTypes
        type = testType.first
        hasTypeVariable = testType.second
    }

    @Unroll
    def "resolving return type #method.genericReturnType does not have type parameters"() {
        expect:
        !JavaReflectionUtil.hasTypeVariable(
            JavaReflectionUtil.resolveMethodReturnType(JavaReflectionUtilTestMethods.InterfaceRealizingTypeParameter, method)
        )

        where:
        method << (JavaReflectionUtilTestMethods.getDeclaredMethods() as List)
        type = method.genericReturnType
    }

    private static List<Tuple2<Type, Boolean>> getTestedTypes() {
        def testedTypes = JavaReflectionUtilTestMethods.getDeclaredMethods().collect {
            new Tuple2(it.genericReturnType, it.name.contains('TypeVariable'))
        }
        assert testedTypes.size() == 15
        return testedTypes
    }
}
