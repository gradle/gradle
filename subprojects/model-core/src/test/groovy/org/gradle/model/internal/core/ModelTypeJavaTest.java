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

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests that need full static typing.
 */
public class ModelTypeJavaTest {

    @Test
    public void testBuildType() throws Exception {
        assertEquals(new ModelType<Map<String, Integer>>() {}, buildMap(ModelType.of(String.class), ModelType.of(Integer.class)));
    }

    static <K, V> ModelType<Map<K, V>> buildMap(ModelType<K> k, ModelType<V> v) {
        return new ModelType.Builder<Map<K, V>>() {}
                .where(new ModelType.Parameter<K>() {}, k)
                .where(new ModelType.Parameter<V>() {}, v)
                .build();
    }

}
