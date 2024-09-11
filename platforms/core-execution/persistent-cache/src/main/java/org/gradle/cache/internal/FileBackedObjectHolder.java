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
import org.gradle.cache.FileAccess;
import org.gradle.cache.ObjectHolder;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.serialize.Serializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.function.Supplier;

public class FileBackedObjectHolder<T> implements ObjectHolder<T> {
    private final FileAccess fileAccess;
    private final Serializer<T> serializer;
    private final Chmod chmod;
    private final File cacheFile;

    public FileBackedObjectHolder(File cacheFile, FileAccess fileAccess, Serializer<T> serializer, Chmod chmod) {
        this.cacheFile = cacheFile;
        this.fileAccess = fileAccess;
        this.serializer = serializer;
        this.chmod = chmod;
    }

    @Override
    public T get() {
        return fileAccess.readFile((Supplier<T>) this::deserialize);
    }

    @Override
    public void set(final T newValue) {
        fileAccess.writeFile(() -> serialize(newValue));
    }

    @Override
    public T update(final UpdateAction<T> updateAction) {
        class Updater implements Runnable {
            private final UpdateAction<T> updateAction;

            private T result;

            private Updater(UpdateAction<T> updateAction) {
                this.updateAction = updateAction;
            }

            @Override
            public void run() {
                T oldValue = deserialize();
                result = updateAction.update(oldValue);
                serialize(result);
            }
        }

        Updater updater = new Updater(updateAction);
        fileAccess.updateFile(updater);
        return updater.result;
    }

    @Override
    public T maybeUpdate(UpdateAction<T> updateAction) {
        class MaybeUpdater implements Runnable {
            private final UpdateAction<T> updateAction;

            private T result;

            private MaybeUpdater(UpdateAction<T> updateAction) {
                this.updateAction = updateAction;
            }

            @Override
            public void run() {
                T oldValue = deserialize();
                result = updateAction.update(oldValue);
                if (!(oldValue == null && result == null) && (oldValue == null || result == null || !oldValue.equals(result))) {
                    serialize(result);
                } else {
                    result = oldValue;
                }
            }
        }

        MaybeUpdater maybeUpdater = new MaybeUpdater(updateAction);
        fileAccess.updateFile(maybeUpdater);
        return maybeUpdater.result;
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
