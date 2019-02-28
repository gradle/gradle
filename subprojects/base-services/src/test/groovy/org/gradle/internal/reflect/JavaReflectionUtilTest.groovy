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

import com.google.common.reflect.TypeToken
import org.gradle.testing.internal.util.Specification
import spock.lang.Unroll

import java.util.function.BiConsumer

class JavaReflectionUtilTest extends Specification {

    @Unroll
    def "#type has type variable: #hasTypeVariable"() {
        expect:
        JavaReflectionUtil.hasTypeVariable(type.getType()) == hasTypeVariable

        where:
        type                                                              | hasTypeVariable
        new TypeToken<Class<?>>() {}                                      | false
        new TypeToken<Class<String>>() {}                                 | false
        new TypeToken<Class<String>[]>() {}                               | false
        TypeToken.of(String.class)                                        | false
        TypeToken.of(String[].class)                                      | false
        new TypeToken<List<BiConsumer<String, Collection<Integer>>>>() {} | false
        new TypeToken<List<? extends Collection<String>>>() {}            | false
        new TypeToken<List<? super List<String>>>() {}                    | false
        new TypeToken<BiConsumer<List<String>, List<String>>>() {}        | false
        genericReturnType("simpleGenericReturnType")                      | true
        genericReturnType("encapsulatedTypeVariable")                     | true
        genericReturnType("arrayTypeWithTypeVariable")                    | true
        genericReturnType("complexTypeWithTypeVariable")                  | true
        genericReturnType("anotherComplexTypeWithTypeVariable")           | true
        genericReturnType("complexTypeWithArrayTypeVariable")             | true
    }

    private static TypeToken genericReturnType(String methodName) {
        return TypeToken.of(JavaReflectionUtilTestMethods.getDeclaredMethod(methodName).genericReturnType)
    }
}
