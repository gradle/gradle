/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.cache.internal.btree;

import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

public class MapDBPersistentIndexedCacheTest {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    private final Serializer<String> stringSerializer = new DefaultSerializer<String>();
    private final Serializer<Integer> integerSerializer = new DefaultSerializer<Integer>();
    private MapDBPersistentIndexedCache<String, Integer> cache;
    private TestFile cacheFile;

    @Before
    public void setup() {
        cacheFile = tmpDir.file("cache.bin");
    }

    private void createCache() {
        cache = new MapDBPersistentIndexedCache<String, Integer>(cacheFile, stringSerializer, integerSerializer);
    }

    @Test
    public void getReturnsNullWhenEntryDoesNotExist() {
        createCache();
        assertNull(cache.get("unknown"));
        cache.close();
    }

    @Test
    public void persistsAddedEntries() {
        createCache();
        checkAdds(1, 2, 3, 4, 5);
        cache.close();
    }

    @Test
    public void persistsAddedEntriesInReverseOrder() {
        createCache();
        checkAdds(5, 4, 3, 2, 1);
        cache.close();
    }

    @Test
    public void persistsAddedEntriesOverMultipleBlocks() {
        createCache();
        checkAdds(3, 2, 11, 5, 7, 1, 10, 8, 9, 4, 6, 0);
        cache.close();
    }

    @Test
    public void persistsUpdates() {
        createCache();
        checkUpdates(3, 2, 11, 5, 7, 1, 10, 8, 9, 4, 6, 0);
        cache.close();
    }

    @Test
    public void handlesUpdatesWhenValueSizeDecreases() {
        MapDBPersistentIndexedCache<String, List<Integer>> cache = new MapDBPersistentIndexedCache<String, List<Integer>>(
            tmpDir.file("listcache.bin"), stringSerializer, new DefaultSerializer<List<Integer>>());

        List<Integer> values = Arrays.asList(3, 2, 11, 5, 7, 1, 10, 8, 9, 4, 6, 0);
        Map<Integer, List<Integer>> updated = new LinkedHashMap<Integer, List<Integer>>();

        for (int i = 10; i > 0; i--) {
            for (Integer value : values) {
                String key = String.format("key_%d", value);
                List<Integer> newValue = new ArrayList<Integer>(i);
                for (int j = 0; j < i * 2; j++) {
                    newValue.add(j);
                }
                cache.put(key, newValue);
                updated.put(value, newValue);
            }

            for (Map.Entry<Integer, List<Integer>> entry : updated.entrySet()) {
                String key = String.format("key_%d", entry.getKey());
                assertThat(cache.get(key), equalTo(entry.getValue()));
            }
        }

        cache.reset();

        for (Map.Entry<Integer, List<Integer>> entry : updated.entrySet()) {
            String key = String.format("key_%d", entry.getKey());
            assertThat(cache.get(key), equalTo(entry.getValue()));
        }

        cache.close();
    }

    @Test
    public void handlesUpdatesWhenValueSizeIncreases() {
        MapDBPersistentIndexedCache<String, List<Integer>> cache = new MapDBPersistentIndexedCache<String, List<Integer>>(
            tmpDir.file("listcache.bin"), stringSerializer, new DefaultSerializer<List<Integer>>());

        List<Integer> values = Arrays.asList(3, 2, 11, 5, 7, 1, 10, 8, 9, 4, 6, 0);
        Map<Integer, List<Integer>> updated = new LinkedHashMap<Integer, List<Integer>>();

        for (int i = 1; i < 10; i++) {
            for (Integer value : values) {
                String key = String.format("key_%d", value);
                List<Integer> newValue = new ArrayList<Integer>(i);
                for (int j = 0; j < i * 2; j++) {
                    newValue.add(j);
                }
                cache.put(key, newValue);
                updated.put(value, newValue);
            }

            for (Map.Entry<Integer, List<Integer>> entry : updated.entrySet()) {
                String key = String.format("key_%d", entry.getKey());
                assertThat(cache.get(key), equalTo(entry.getValue()));
            }
        }

        cache.reset();

        for (Map.Entry<Integer, List<Integer>> entry : updated.entrySet()) {
            String key = String.format("key_%d", entry.getKey());
            assertThat(cache.get(key), equalTo(entry.getValue()));
        }

        cache.close();
    }

    @Test
    public void persistsAddedEntriesAfterReopen() {
        createCache();

        checkAdds(1, 2, 3, 4);

        cache.reset();

        checkAdds(5, 6, 7, 8);
        cache.close();
    }

    @Test
    public void persistsReplacedEntries() {
        createCache();

        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.put("key_3", 3);
        cache.put("key_4", 4);
        cache.put("key_5", 5);

        cache.put("key_1", 1);
        cache.put("key_4", 12);

        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        assertThat(cache.get("key_3"), equalTo(3));
        assertThat(cache.get("key_4"), equalTo(12));
        assertThat(cache.get("key_5"), equalTo(5));

        cache.reset();

        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        assertThat(cache.get("key_3"), equalTo(3));
        assertThat(cache.get("key_4"), equalTo(12));
        assertThat(cache.get("key_5"), equalTo(5));

        cache.close();
    }

    @Test
    public void canHandleLargeNumberOfEntries() {
        createCache();
        int count = 2000;
        List<Integer> values = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            values.add(i);
        }
        checkAdds(values);
        cache.close();
    }

    @Test
    public void persistsRemovalOfEntries() {
        createCache();
        checkAddsAndRemoves(1, 2, 3, 4, 5);
        cache.close();
    }

    @Test
    public void handlesOpeningACacheFileThatIsBadlyFormed() throws Exception {
        cacheFile.createNewFile();
        cacheFile.write("some junk");

        createCache();

        assertNull(cache.get("key_1"));
        cache.put("key_1", 99);

        cache.reset();

        assertThat(cache.get("key_1"), equalTo(99));

        cache.close();
    }

    @Test
    public void canUseFileAsKey() {
        MapDBPersistentIndexedCache<File, Integer> cache = new MapDBPersistentIndexedCache<File, Integer>(
            cacheFile, new DefaultSerializer<File>(), integerSerializer);

        cache.put(new File("file"), 1);
        cache.put(new File("dir/file"), 2);
        cache.put(new File("File"), 3);

        assertThat(cache.get(new File("file")), equalTo(1));
        assertThat(cache.get(new File("dir/file")), equalTo(2));
        assertThat(cache.get(new File("File")), equalTo(3));

        cache.close();
    }

    @Test
    public void canReadFromReadOnlyFile() {
        createCache();
        cache.put("1", 1);
        cache.close();
        cacheFile.setReadOnly();
        createCache();
        assertThat(cache.get("1"), equalTo(1));
        cache.close();
    }

    @Test
    public void clearDiscardsAllEntries() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        assertThat(cache.get("key_1"), equalTo(1));

        cache.clear();

        assertNull(cache.get("key_1"));
        assertNull(cache.get("key_2"));

        cache.put("key_3", 3);
        assertThat(cache.get("key_3"), equalTo(3));

        cache.close();
    }

    private void checkAdds(Integer... values) {
        checkAdds(Arrays.asList(values));
    }

    private Map<String, Integer> checkAdds(Iterable<Integer> values) {
        Map<String, Integer> added = new LinkedHashMap<String, Integer>();

        for (Integer value : values) {
            String key = String.format("key_%d", value);
            cache.put(key, value);
            added.put(String.format("key_%d", value), value);
        }

        for (Map.Entry<String, Integer> entry : added.entrySet()) {
            assertThat(cache.get(entry.getKey()), equalTo(entry.getValue()));
        }

        cache.reset();

        for (Map.Entry<String, Integer> entry : added.entrySet()) {
            assertThat(cache.get(entry.getKey()), equalTo(entry.getValue()));
        }

        return added;
    }

    private void checkUpdates(Integer... values) {
        for (int i = 0; i < 10; i++) {
            Map<Integer, Integer> updated = new LinkedHashMap<Integer, Integer>();
            for (Integer value : values) {
                String key = String.format("key_%d", value);
                int newValue = value + (i * 100);
                cache.put(key, newValue);
                updated.put(value, newValue);
            }

            for (Map.Entry<Integer, Integer> entry : updated.entrySet()) {
                String key = String.format("key_%d", entry.getKey());
                assertThat(cache.get(key), equalTo(entry.getValue()));
            }
        }

        cache.reset();

        for (Integer value : values) {
            String key = String.format("key_%d", value);
            assertThat(cache.get(key), notNullValue());
        }
    }

    private void checkAddsAndRemoves(Integer... values) {
        checkAdds(values);

        for (Integer value : values) {
            String key = String.format("key_%d", value);
            assertThat(cache.get(key), notNullValue());
            cache.remove(key);
            assertThat(cache.get(key), nullValue());
        }

        cache.reset();

        for (Integer value : values) {
            String key = String.format("key_%d", value);
            assertThat(cache.get(key), nullValue());
        }
    }
}
