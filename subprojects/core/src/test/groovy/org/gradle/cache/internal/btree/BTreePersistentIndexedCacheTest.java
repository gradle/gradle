/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class BTreePersistentIndexedCacheTest {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final Serializer<String> stringSerializer = new DefaultSerializer<String>();
    private final Serializer<Integer> integerSerializer = new DefaultSerializer<Integer>();
    private BTreePersistentIndexedCache<String, Integer> cache;
    private TestFile cacheFile;

    @Before
    public void setup() {
        cacheFile = tmpDir.file("cache.bin");
        cache = new BTreePersistentIndexedCache<String, Integer>(cacheFile, stringSerializer, integerSerializer, (short) 4, 100);
    }

    @Test
    public void getReturnsNullWhenEntryDoesNotExist() {
        assertNull(cache.get("unknown"));
        cache.verify();
    }

    @Test
    public void persistsAddedEntries() {
        checkAdds(1, 2, 3, 4, 5);
        cache.verify();
    }

    @Test
    public void persistsAddedEntriesInReverseOrder() {
        checkAdds(5, 4, 3, 2, 1);
        cache.verify();
    }

    @Test
    public void persistsAddedEntriesOverMultipleIndexBlocks() {
        checkAdds(3, 2, 11, 5, 7, 1, 10, 8, 9, 4, 6, 0);
        cache.verify();
    }

    @Test
    public void persistsAddedEntriesAfterReopen() {
        checkAdds(1, 2, 3, 4);

        cache.reset();

        checkAdds(5, 6, 7, 8);
        cache.verify();
    }
    
    @Test
    public void persistsReplacedEntries() {

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

        cache.verify();
    }

    @Test
    public void reusesEmptySpaceWhenPuttingEntries() {
        BTreePersistentIndexedCache<String, String> cache = new BTreePersistentIndexedCache<String, String>(cacheFile, stringSerializer, stringSerializer, (short) 4, 100);

        cache.put("key_1", "abcd");
        cache.put("key_2", "abcd");
        cache.put("key_3", "abcd");
        cache.put("key_4", "abcd");
        cache.put("key_5", "abcd");

        long len = cacheFile.length();
        assertThat(len, greaterThan(0L));

        cache.put("key_1", "1234");
        assertThat(cacheFile.length(), equalTo(len));

        cache.remove("key_1");
        cache.put("key_new", "a1b2");
        assertThat(cacheFile.length(), equalTo(len));

        cache.put("key_new", "longer value");
        assertThat(cacheFile.length(), greaterThan(len));
        len = cacheFile.length();

        cache.put("key_1", "1234");
        assertThat(cacheFile.length(), equalTo(len));
    }
    
    @Test
    public void canHandleLargeNumberOfEntries() {

        int count = 2000;
        List<Integer> values = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            values.add(i);
        }

        checkAddsAndRemoves(null, values);

        long len = cacheFile.length();

        checkAddsAndRemoves(Collections.<Integer>reverseOrder(), values);

        // need to make this better
        assertThat(cacheFile.length(), lessThan((long)(1.4 * len)));

        checkAdds(values);
        
        // need to make this better
        assertThat(cacheFile.length(), lessThan((long) (1.4 * 1.4 * len)));
    }

    @Test
    public void persistsRemovalOfEntries() {
        checkAddsAndRemoves(1, 2, 3, 4, 5);
        cache.verify();
    }

    @Test
    public void persistsRemovalOfEntriesInReverse() {
        checkAddsAndRemoves(Collections.<Integer>reverseOrder(), 1, 2, 3, 4, 5);
        cache.verify();
    }

    @Test
    public void persistsRemovalOfEntriesOverMultipleIndexBlocks() {
        checkAddsAndRemoves(4, 12, 9, 1, 3, 10, 11, 7, 8, 2, 5, 6);
        cache.verify();
    }

    @Test
    public void removalRedistributesRemainingEntriesWithLeftSibling() {
        // Ends up with: 1 2 3 -> 4 <- 5 6
        checkAdds(1, 2, 5, 6, 4, 3);
        cache.verify();
        cache.remove("key_5");
        cache.verify();
    }

    @Test
    public void removalMergesRemainingEntriesIntoLeftSibling() {
        // Ends up with: 1 2 -> 3 <- 4 5
        checkAdds(1, 2, 4, 5, 3);
        cache.verify();
        cache.remove("key_4");
        cache.verify();
    }

    @Test
    public void removalRedistributesRemainingEntriesWithRightSibling() {
        // Ends up with: 1 2 -> 3 <- 4 5 6
        checkAdds(1, 2, 4, 5, 3, 6);
        cache.verify();
        cache.remove("key_2");
        cache.verify();
    }

    @Test
    public void removalMergesRemainingEntriesIntoRightSibling() {
        // Ends up with: 1 2 -> 3 <- 4 5
        checkAdds(1, 2, 4, 5, 3);
        cache.verify();
        cache.remove("key_2");
        cache.verify();
    }

    @Test
    public void handlesBadlyFormedCacheFile() throws IOException {
        cacheFile.assertIsFile();
        cacheFile.write("some junk");

        BTreePersistentIndexedCache<String, Integer> cache = new BTreePersistentIndexedCache<String, Integer>(cacheFile, stringSerializer, integerSerializer);

        assertNull(cache.get("key_1"));
        cache.put("key_1", 99);

        RandomAccessFile file = new RandomAccessFile(cacheFile, "rw");
        file.setLength(file.length() - 10);

        cache.reset();

        assertNull(cache.get("key_1"));
        cache.verify();
    }

    @Test
    public void canUseFileAsKey() {
        BTreePersistentIndexedCache<File, Integer> cache = new BTreePersistentIndexedCache<File, Integer>(cacheFile, new DefaultSerializer<File>(), integerSerializer);

        cache.put(new File("file"), 1);
        cache.put(new File("dir/file"), 2);
        cache.put(new File("File"), 3);

        assertThat(cache.get(new File("file")), equalTo(1));
        assertThat(cache.get(new File("dir/file")), equalTo(2));
        assertThat(cache.get(new File("File")), equalTo(3));
    }

    @Test
    public void handlesKeysWithSameHashCode() {
        String key1 = new String(new byte[]{2, 31});
        String key2 = new String(new byte[]{1, 62});
        cache.put(key1, 1);
        cache.put(key2, 2);

        assertThat(cache.get(key1), equalTo(1));
        assertThat(cache.get(key2), equalTo(2));
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

    private void checkAddsAndRemoves(Integer... values) {
        checkAddsAndRemoves(null, values);
    }

    private void checkAddsAndRemoves(Comparator<Integer> comparator, Integer... values) {
        checkAddsAndRemoves(comparator, Arrays.asList(values));
    }

    private void checkAddsAndRemoves(Comparator<Integer> comparator, Collection<Integer> values) {
        checkAdds(values);

        List<Integer> deleteValues = new ArrayList<Integer>(values);
        Collections.sort(deleteValues, comparator);
        for (Integer value : deleteValues) {
            String key = String.format("key_%d", value);
            assertThat(cache.get(key), notNullValue());
            cache.remove(key);
            assertThat(cache.get(key), nullValue());
        }

        cache.reset();
        cache.verify();

        for (Integer value : deleteValues) {
            String key = String.format("key_%d", value);
            assertThat(cache.get(key), nullValue());
        }
    }

}