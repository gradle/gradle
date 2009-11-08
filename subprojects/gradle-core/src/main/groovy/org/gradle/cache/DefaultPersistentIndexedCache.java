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
    private final Serializer<V> serializer;

    public DefaultPersistentIndexedCache(PersistentCache backingCache, Serializer<V> serializer) {
        this.backingCache = backingCache;
        this.serializer = serializer;
    }

    public V get(K key) {
        File stateFile = getStateFile(key);
        try {
            InputStream inStr = new BufferedInputStream(new FileInputStream(stateFile));
            try {
                return serializer.read(inStr);
            } finally {
                inStr.close();
            }
        } catch (FileNotFoundException e) {
            // Ok
            return null;
        } catch (Exception e) {
            throw new UncheckedIOException(e);
        }
    }

    public void put(K key, V value) {
        File stateFile = getStateFile(key);
        stateFile.getParentFile().mkdirs();

        try {
            OutputStream outStr = new BufferedOutputStream(new FileOutputStream(stateFile));
            try {
                serializer.write(outStr, value);
            } finally {
                outStr.close();
            }
        } catch (Exception e) {
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
            fileName = getStateFileName((File) key);
        } else if (key instanceof String) {
            fileName = getStateFileName((String) key);
        } else if (key == null) {
            fileName = "null";
        } else {
            throw new UnsupportedOperationException("Not implemented yet");
        }
        if (fileName.length() < 5) {
            return new File(backingCache.getBaseDir(), String.format("none/%s.bin", fileName));
        }
        return new File(backingCache.getBaseDir(), String.format("%s/%s/%s.bin", fileName.substring(0, 2),
                fileName.subSequence(0, 4), fileName));
    }

    private String getStateFileName(String key) {
        return encode(key, key);
    }

    private String getStateFileName(File keyFile) {
        return encode(keyFile.getAbsolutePath(), keyFile.getName());
    }

    private String encode(String key, String displayName) {
        StringBuilder name = new StringBuilder(displayName);
        boolean encode = !key.equals(displayName);
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                name.setCharAt(i, '_');
                encode = true;
            }
            else if (Character.isUpperCase(ch)) {
                name.setCharAt(i, Character.toLowerCase(ch));
                encode = true;
            }
        }
        name.append('_');
        if (!encode) {
            return name.toString();
        }
        name.append(HashUtil.createHash(key));
        return name.toString();
    }
}
