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

package org.gradle.cache;

import org.gradle.api.GradleException;

import java.io.*;

public class SimpleStateCache<T> implements PersistentStateCache<T> {
    private final Serializer<T> serializer;
    private final File cacheFile;
    private PersistentCache cache;

    public SimpleStateCache(PersistentCache cache, Serializer<T> serializer) {
        this.cache = cache;
        this.serializer = serializer;
        cacheFile = new File(cache.getBaseDir(), "state.bin");
    }

    public T get() {
        if (!cacheFile.isFile()) {
            return null;
        }
        try {
            InputStream inStr = new BufferedInputStream(new FileInputStream(cacheFile));
            try {
                return serializer.read(inStr);
            } finally {
                inStr.close();
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not read cache value from '%s'.", cacheFile), e);
        }
    }

    public void set(T newValue) {
        try {
            OutputStream outStr = new BufferedOutputStream(new FileOutputStream(cacheFile));
            try {
                serializer.write(outStr, newValue);
            } finally {
                outStr.close();
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not write cache value to '%s'.", cacheFile), e);
        }
        cache.markValid();
    }
}
