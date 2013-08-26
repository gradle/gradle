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

package org.gradle.cache.internal;

import org.gradle.api.GradleException;
import org.gradle.internal.Factory;
import org.gradle.cache.PersistentStateCache;
import org.gradle.messaging.serialize.InputStreamBackedDecoder;
import org.gradle.messaging.serialize.OutputStreamBackedEncoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.*;

public class SimpleStateCache<T> implements PersistentStateCache<T> {
    private final FileAccess fileAccess;
    private final Serializer<T> serializer;
    private final File cacheFile;

    public SimpleStateCache(File cacheFile, FileAccess fileAccess, Serializer<T> serializer) {
        this.cacheFile = cacheFile;
        this.fileAccess = fileAccess;
        this.serializer = serializer;
    }

    public T get() {
        return fileAccess.readFile(new Factory<T>() {
            public T create() {
                return deserialize();
            }
        });
    }

    public void set(final T newValue) {
        fileAccess.writeFile(new Runnable() {
            public void run() {
                serialize(newValue);
            }
        });
    }

    public void update(final UpdateAction<T> updateAction) {
        fileAccess.updateFile(new Runnable() {
            public void run() {
                T oldValue = deserialize();
                T newValue = updateAction.update(oldValue);
                serialize(newValue);
            }
        });
    }

    private void serialize(T newValue) {
        try {
            OutputStreamBackedEncoder encoder = new OutputStreamBackedEncoder(new BufferedOutputStream(new FileOutputStream(cacheFile)));
            try {
                serializer.write(encoder, newValue);
            } finally {
                encoder.close();
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not write cache value to '%s'.", cacheFile), e);
        }
    }

    private T deserialize() {
        if (!cacheFile.isFile()) {
            return null;
        }
        try {
            InputStreamBackedDecoder decoder = new InputStreamBackedDecoder(new BufferedInputStream(new FileInputStream(cacheFile)));
            try {
                return serializer.read(decoder);
            } finally {
                decoder.close();
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not read cache value from '%s'.", cacheFile), e);
        }
    }
}
