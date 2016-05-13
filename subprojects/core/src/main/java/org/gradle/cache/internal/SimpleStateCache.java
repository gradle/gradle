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
import org.gradle.cache.PersistentStateCache;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.serialize.Serializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class SimpleStateCache<T> implements PersistentStateCache<T> {
    private final FileAccess fileAccess;
    private final Serializer<T> serializer;
    private final Chmod chmod;
    private final File cacheFile;

    public SimpleStateCache(File cacheFile, FileAccess fileAccess, Serializer<T> serializer, Chmod chmod) {
        this.cacheFile = cacheFile;
        this.fileAccess = fileAccess;
        this.serializer = serializer;
        this.chmod = chmod;
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
            if (!cacheFile.isFile()) {
                cacheFile.createNewFile();
            }
            chmod.chmod(cacheFile.getParentFile(), 0700); // read-write-execute for user only
            chmod.chmod(cacheFile, 0600); // read-write for user only
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
