/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core;

import org.gradle.model.internal.type.ModelType;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests that need full static typing.
 */
public class ModelTypeJavaTest {
    static class Nested<T> {
        class Child<S extends Number & Runnable> { }
    }

    <T extends Number, S extends T, R> void m1(T t, S s, R r) { }
    <T extends Number & Runnable, S extends T> void m1(S s) { }
    void m2(List<String>... lists) { }
    void m4(List<? super Number>... lists) { }
    void m5(Collection<String>... collections) { }

    @Test
    public void testNestedParameterizedType() {
// Suppress - checkstyle gets confused with type params on the outer type
//CHECKSTYLE:OFF
        ModelType<?> type = new ModelType<Nested<? super Long>.Child<? extends Runnable>>() {};
        assertEquals(type.getDisplayName(), "ModelTypeJavaTest.Nested<? super Long>.Child<? extends Runnable>");
        assertEquals(type.toString(), "org.gradle.model.internal.core.ModelTypeJavaTest.Nested<? super java.lang.Long>.Child<? extends java.lang.Runnable>");

        ModelType<?> listType = new ModelType<List<? extends Nested<Number>.Child<? extends Runnable>>>() {};

        assertEquals(listType.getDisplayName(), "List<? extends ModelTypeJavaTest.Nested<Number>.Child<? extends Runnable>>");
        assertEquals(listType.toString(), "java.util.List<? extends org.gradle.model.internal.core.ModelTypeJavaTest.Nested<java.lang.Number>.Child<? extends java.lang.Runnable>>");
//CHECKSTYLE:ON
    }

    @Test
    public void testBuildType() throws Exception {
        assertEquals(new ModelType<Map<String, Integer>>() {}, buildMap(ModelType.of(String.class), ModelType.of(Integer.class)));
        assertEquals(new ModelType<Map<String, Integer>>() {}.hashCode(), buildMap(ModelType.of(String.class), ModelType.of(Integer.class)).hashCode());
    }

    static <K, V> ModelType<Map<K, V>> buildMap(ModelType<K> k, ModelType<V> v) {
        return new ModelType.Builder<Map<K, V>>() {}
                .where(new ModelType.Parameter<K>() {}, k)
                .where(new ModelType.Parameter<V>() {}, v)
                .build();
    }

}
