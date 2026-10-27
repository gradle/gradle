/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.fixtures.codegen;

import java.util.Collection;


public interface ClassToKClass {

    @SuppressWarnings("rawtypes")
    void rawClass(Class type);

    void unknownClass(Class<?> type);

    void invariantClass(Class<Number> type);

    void covariantClass(Class<? extends Number> type);

    void contravariantClass(Class<? super Integer> type);

    void varargOfClasses(Class<?>... types);

    void arrayOfClasses(Class<?>[] types);

    void collectionOfClasses(Collection<Class<? extends Number>> types);

    <T> T methodParameterizedClass(Class<T> type);

    <T extends Number> T covariantMethodParameterizedClass(Class<T> type);

    <T> T methodParameterizedCovariantClass(Class<? extends T> type);

    <T> T methodParameterizedContravariantClass(Class<? super T> type);

    <T extends Number> T covariantMethodParameterizedCovariantClass(Class<? extends T> type);

    <T extends Number> T covariantMethodParameterizedContravariantClass(Class<? super T> type);
}
