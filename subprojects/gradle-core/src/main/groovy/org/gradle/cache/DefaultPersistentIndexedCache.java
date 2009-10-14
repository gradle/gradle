/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.UncheckedIOException;
import org.gradle.util.HashUtil;

import java.io.*;

public class DefaultPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private final PersistentCache backingCache;

    public DefaultPersistentIndexedCache(PersistentCache backingCache) {
        this.backingCache = backingCache;
    }

    public V get(K key) {
        File stateFile = getStateFile(key);
        if (!stateFile.exists()) {
            return null;
        }
        try {
            ObjectInputStream inStr = new ObjectInputStream(new BufferedInputStream(new FileInputStream(stateFile)));
            try {
                return (V) inStr.readObject();
            } finally {
                inStr.close();
            }
        } catch (StreamCorruptedException e) {
            // Ignore badly formed files
            return null;
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    public void put(K key, V value) {
        try {
            File stateFile = getStateFile(key);
            stateFile.getParentFile().mkdirs();
            ObjectOutputStream outStr = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(
                    stateFile)));
            try {
                outStr.writeObject(value);
            } finally {
                outStr.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        backingCache.update();
    }

    public void remove(K key) {
        getStateFile(key).delete();
        backingCache.update();
    }

    private File getStateFile(K key) {
        String fileName;
        if (key instanceof File) {
            File keyFile = (File) key;
            fileName = String.format("%s_%s", keyFile.getName(), HashUtil.createHash(keyFile.getAbsolutePath()));
        } else if (key instanceof CharSequence) {
            fileName = key.toString().replace(File.separatorChar, '_').replace('/', '_');
        } else if (key == null) {
            fileName = "null";
        } else {
            throw new UnsupportedOperationException("Not implemented yet");
        }
        if (fileName.length() < 5) {
            return new File(backingCache.getBaseDir(), String.format("%s.bin", fileName));
        }
        return new File(backingCache.getBaseDir(), String.format("%s/%s/%s.bin", fileName.substring(0, 2),
                fileName.subSequence(0, 4), fileName));
    }
}
