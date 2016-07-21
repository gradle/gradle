/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.util.Collections;
import java.util.List;

class OrderInsensitiveFileCollectionHashBuilder implements FileCollectionHashBuilder {
    private final List<Entry> entries;
    private final TaskCacheKeyBuilder builder;

    public OrderInsensitiveFileCollectionHashBuilder(int size, TaskCacheKeyBuilder builder) {
        this.entries = Lists.newArrayListWithCapacity(size);
        this.builder = builder;
    }

    @Override
    public void hash(String key, HashCode hashCode) {
        entries.add(new Entry(key, hashCode.asBytes()));
    }

    @Override
    public void close() {
        Collections.sort(entries);
        for (Entry entry : entries) {
            entry.appendToCacheKey(builder);
        }
    }

    private static class Entry implements Comparable<Entry> {
        private final String key;
        private final byte[] hashCode;

        public Entry(String key, byte[] hashCode) {
            this.key = key;
            this.hashCode = hashCode;
        }

        public void appendToCacheKey(TaskCacheKeyBuilder hasher) {
            hasher.putString(key);
            hasher.putBytes(hashCode);
        }

        @Override
        public int compareTo(Entry o) {
            int result = key.compareTo(o.key);
            if (result == 0) {
                result = hashCode.length - o.hashCode.length;
                if (result == 0) {
                    for (int idx = 0; idx < hashCode.length; idx++) {
                        result = hashCode[idx] - o.hashCode[idx];
                        if (result != 0) {
                            break;
                        }
                    }
                }
            }
            return result;
        }
    }
}
