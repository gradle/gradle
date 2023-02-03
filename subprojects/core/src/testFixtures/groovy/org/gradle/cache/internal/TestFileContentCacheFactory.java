/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.cache.internal;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.List;

public class TestFileContentCacheFactory implements FileContentCacheFactory {

    private final List<File> calculationLog = Lists.newArrayList();

    public List<File> getCalculationLog() {
        return calculationLog;
    }

    @Override
    public <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, final Calculator<? extends V> calculator, Serializer<V> serializer) {
        return new FileContentCache<V>() {
            final LoadingCache<File, V> cache = CacheBuilder.newBuilder().build(new CacheLoader<File, V>() {
                @Override
                public V load(File file) {
                    calculationLog.add(file);
                    return calculator.calculate(file, file.isFile());
                }
            });

            @Override
            public V get(File file) {
                try {
                    return cache.getUnchecked(file);
                } catch (UncheckedExecutionException e) {
                    Throwables.throwIfUnchecked(e.getCause());
                    throw new RuntimeException(e.getCause());
                }
            }
        };
    }
}
