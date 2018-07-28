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

import java.io.Serializable;
import java.util.List;


public interface ClassToKClassParameterizedType<T extends Serializable> {

    T invariantClass(Class<T> type, List<T> list);

    T covariantClass(Class<? extends T> type, List<T> list);

    T contravariantClass(Class<? super T> type, List<T> list);

    <V extends T> V covariantMethodParameterizedInvariantClass(Class<V> type, List<V> list);

    <V extends T> V covariantMethodParameterizedCovariantClass(Class<? extends V> type, List<? extends V> list);

    <V extends T> V covariantMethodParameterizedContravariantClass(Class<? super V> type, List<? extends V> list);
}
