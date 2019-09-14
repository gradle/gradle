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

package org.gradle.api.java.archives.internal

import com.google.common.collect.testing.MapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;
import junit.framework.Test
import junit.framework.TestSuite
import org.junit.runners.AllTests;
import org.junit.runner.RunWith

@RunWith(AllTests.class)
class DefaultAttributesMapTest {
    static Test suite() {
        TestSuite suite = new TestSuite("All tests")
        suite.addTest(tests("Simple", new TestStringMapGenerator() {
            @Override
            protected Map<String, String> create(Map.Entry<String, String>[] entries) {
                return populate(new DefaultAttributes(), entries)
            }
        }));
        return suite
    }

    private static TestSuite tests(final String name, TestStringMapGenerator generator) {
        return MapTestSuiteBuilder
            .using(generator)
            .named(name)
            .withFeatures(
                MapFeature.GENERAL_PURPOSE,
                MapFeature.RESTRICTS_KEYS,
                MapFeature.RESTRICTS_VALUES,
                CollectionFeature.KNOWN_ORDER,
                CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                CollectionFeature.SUPPORTS_REMOVE,
                CollectionSize.ANY)
            .createTestSuite();
    }

    private static <T, M extends Map<T, String>> M populate(M map, Map.Entry<T, String>[] entries) {
        for (Map.Entry<T, String> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
