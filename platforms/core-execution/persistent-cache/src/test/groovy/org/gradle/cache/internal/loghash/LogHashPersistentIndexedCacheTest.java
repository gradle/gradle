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
package org.gradle.cache.internal.loghash;

import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LogHashPersistentIndexedCacheTest {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    private final Serializer<String> stringSerializer = new DefaultSerializer<String>();
    private final Serializer<Integer> integerSerializer = new DefaultSerializer<Integer>();
    private LogHashPersistentIndexedCache<String, Integer> cache;
    private TestFile cacheFile;

    @Before
    public void setup() {
        cacheFile = tmpDir.file("cache.bin");
    }

    private void createCache() {
        cache = new LogHashPersistentIndexedCache<>(cacheFile, stringSerializer, integerSerializer);
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
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.put("key_3", 3);

        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        assertThat(cache.get("key_3"), equalTo(3));

        cache.close();
    }

    @Test
    public void persistsEntriesAcrossCloseAndReopen() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.put("key_3", 3);
        cache.close();

        createCache();
        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        assertThat(cache.get("key_3"), equalTo(3));
        cache.close();
    }

    @Test
    public void persistsEntriesAcrossReset() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);

        cache.reset();

        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        cache.close();
    }

    @Test
    public void persistsReplacedEntries() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.put("key_1", 10);
        cache.put("key_2", 20);

        assertThat(cache.get("key_1"), equalTo(10));
        assertThat(cache.get("key_2"), equalTo(20));

        cache.close();

        createCache();
        assertThat(cache.get("key_1"), equalTo(10));
        assertThat(cache.get("key_2"), equalTo(20));
        cache.close();
    }

    @Test
    public void persistsRemovalOfEntries() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.put("key_3", 3);
        cache.remove("key_2");

        assertThat(cache.get("key_1"), equalTo(1));
        assertNull(cache.get("key_2"));
        assertThat(cache.get("key_3"), equalTo(3));

        cache.close();

        createCache();
        assertThat(cache.get("key_1"), equalTo(1));
        assertNull(cache.get("key_2"));
        assertThat(cache.get("key_3"), equalTo(3));
        cache.close();
    }

    @Test
    public void persistsEntriesAcrossFlush() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.flush();

        // After flush, entries should still be readable (from flushedIndex tier)
        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));

        // Add more entries and flush again
        cache.put("key_3", 3);
        cache.flush();

        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        assertThat(cache.get("key_3"), equalTo(3));

        cache.close();

        // Reopen and verify all entries survived
        createCache();
        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        assertThat(cache.get("key_3"), equalTo(3));
        cache.close();
    }

    @Test
    public void persistsUpdates() {
        createCache();
        for (int i = 0; i < 10; i++) {
            for (int v = 0; v < 12; v++) {
                String key = String.format("key_%d", v);
                int newValue = v + (i * 100);
                cache.put(key, newValue);
            }
        }

        // Check latest values
        for (int v = 0; v < 12; v++) {
            String key = String.format("key_%d", v);
            assertThat(cache.get(key), equalTo(v + 900));
        }

        cache.reset();

        for (int v = 0; v < 12; v++) {
            String key = String.format("key_%d", v);
            assertThat(cache.get(key), equalTo(v + 900));
        }

        cache.close();
    }

    @Test
    public void canHandleLargeNumberOfEntries() {
        createCache();
        int count = 2000;
        Map<String, Integer> expected = new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            String key = String.format("key_%d", i);
            cache.put(key, i);
            expected.put(key, i);
        }

        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            assertThat(cache.get(entry.getKey()), equalTo(entry.getValue()));
        }

        cache.reset();

        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            assertThat(cache.get(entry.getKey()), equalTo(entry.getValue()));
        }

        cache.close();
    }

    @Test
    public void handlesAddsAndRemoves() {
        createCache();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            values.add(i);
        }

        // Add all
        for (Integer value : values) {
            cache.put("key_" + value, value);
        }
        for (Integer value : values) {
            assertThat(cache.get("key_" + value), notNullValue());
        }

        // Remove all
        for (Integer value : values) {
            cache.remove("key_" + value);
            assertThat(cache.get("key_" + value), nullValue());
        }

        cache.reset();

        for (Integer value : values) {
            assertThat(cache.get("key_" + value), nullValue());
        }

        cache.close();
    }

    @Test
    public void handlesOpeningABadlyFormedFile() throws Exception {
        cacheFile.createNewFile();
        cacheFile.write("some junk");

        // The LogHash cache uses .dat/.idx files, not the base .bin file directly
        // Write junk to the .idx file to test corruption handling
        String basePath = cacheFile.getPath();
        String base = basePath.substring(0, basePath.lastIndexOf('.'));
        new File(base + ".idx").getParentFile().mkdirs();
        tmpDir.file(new File(base + ".idx").getName()).write("some junk");

        createCache();

        assertNull(cache.get("key_1"));
        cache.put("key_1", 99);

        cache.reset();

        assertThat(cache.get("key_1"), equalTo(99));

        cache.close();
    }

    @Test
    public void canUseFileAsKey() {
        LogHashPersistentIndexedCache<File, Integer> fileCache = new LogHashPersistentIndexedCache<>(
            cacheFile, new DefaultSerializer<File>(), integerSerializer);

        fileCache.put(new File("file"), 1);
        fileCache.put(new File("dir/file"), 2);
        fileCache.put(new File("File"), 3);

        assertThat(fileCache.get(new File("file")), equalTo(1));
        assertThat(fileCache.get(new File("dir/file")), equalTo(2));
        assertThat(fileCache.get(new File("File")), equalTo(3));

        fileCache.close();
    }

    @Test
    public void supportsConcurrentReads() {
        createCache();
        assertTrue(cache.supportsConcurrentReads());
        cache.close();
    }

    @Test
    public void clearRemovesAllEntries() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.flush();

        cache.clear();

        assertNull(cache.get("key_1"));
        assertNull(cache.get("key_2"));
        cache.close();
    }

    @Test
    public void flushThenAddMoreEntries() {
        createCache();
        cache.put("key_1", 1);
        cache.flush();

        cache.put("key_2", 2);
        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));

        cache.close();

        createCache();
        assertThat(cache.get("key_1"), equalTo(1));
        assertThat(cache.get("key_2"), equalTo(2));
        cache.close();
    }

    @Test
    public void updateAfterFlush() {
        createCache();
        cache.put("key_1", 1);
        cache.flush();

        cache.put("key_1", 10);
        assertThat(cache.get("key_1"), equalTo(10));

        cache.close();

        createCache();
        assertThat(cache.get("key_1"), equalTo(10));
        cache.close();
    }

    @Test
    public void removeAfterFlush() {
        createCache();
        cache.put("key_1", 1);
        cache.put("key_2", 2);
        cache.flush();

        cache.remove("key_1");
        assertNull(cache.get("key_1"));
        assertThat(cache.get("key_2"), equalTo(2));

        cache.close();

        createCache();
        assertNull(cache.get("key_1"));
        assertThat(cache.get("key_2"), equalTo(2));
        cache.close();
    }

    @Test
    public void multipleFlushCycles() {
        createCache();

        for (int cycle = 0; cycle < 5; cycle++) {
            for (int i = 0; i < 20; i++) {
                cache.put("key_" + i, cycle * 100 + i);
            }
            cache.flush();
        }

        // Verify latest values
        for (int i = 0; i < 20; i++) {
            assertThat(cache.get("key_" + i), equalTo(400 + i));
        }

        cache.close();

        // Verify after reopen
        createCache();
        for (int i = 0; i < 20; i++) {
            assertThat(cache.get("key_" + i), equalTo(400 + i));
        }
        cache.close();
    }
}
