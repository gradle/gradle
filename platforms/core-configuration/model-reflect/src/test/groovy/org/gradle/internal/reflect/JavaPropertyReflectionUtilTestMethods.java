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

package org.gradle.internal.reflect;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

public interface JavaPropertyReflectionUtilTestMethods<T> {
    T simpleTypeVariable();

    List<T> encapsulatedTypeVariable();

    T[] arrayTypeWithTypeVariable();

    List<Collection<? extends List<? super T>>[]> complexTypeWithTypeVariable();

    List<BiConsumer<Collection<? super Class<Integer>>, ? extends List<? extends T>>[]> anotherComplexTypeWithTypeVariable();

    List<BiConsumer<Collection<? super Class<Integer>>, ? extends List<T[]>>> complexTypeWithArrayTypeVariable();

    Class<?> wildcardParameterized();

    Class<String> parameterized();

    Class<String>[] genericArrayType();

    String simpleType();

    String[] simpleArrayType();

    List<BiConsumer<String, Collection<Integer>>> complexParameterized();

    List<? extends Collection<String>> wildCardWithlowerBound();

    List<? super List<String>> wildCardWithUpperBound();

    BiConsumer<List<String>, List<String>> anotherComplexParameterized();

    <V extends String> V nonResolvedTypeVariable();

    interface InterfaceRealizingTypeParameter extends JavaPropertyReflectionUtilTestMethods<List<Integer>> {
    }
}
