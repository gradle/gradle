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

package org.gradle.kotlin.dsl.codegen;

import java.util.List;
import java.util.Set;


public interface ClassToKClass {

    void rawClass(Class type);

    void arrayOfRawClasses(Class[] types);

    void varargOfRawClasses(Class... types);

    void listOfRawClasses(List<Class> types);

    void setOfRawClasses(Set<Class> types);

    void unboundedClass(Class<?> type);

    void arrayOfUnboundedClasses(Class<?>[] types);

    void varargOfUnboundedClasses(Class<?>... types);

    void noBoundClass(Class<Number> type);

    void upperBoundClass(Class<? extends Number> type);

    void lowerBoundClass(Class<? super Integer> type);

    <T> void methodParameterizedClass(Class<T> type);

    <T> void boundedMethodParameterizedClass(Class<? super T> type);
}
