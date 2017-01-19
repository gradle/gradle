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

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Nullable;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.internal.Java9ClassReader;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JvmClassHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(JvmClassHasher.class.getName(), Charsets.UTF_8).asBytes();
    private final PersistentIndexedCache<HashCode, HashCode> persistentCache;

    public JvmClassHasher(PersistentIndexedCache<HashCode, HashCode> persistentCache) {
        this.persistentCache = persistentCache;
    }

    /**
     * @return null if the class should not be included in the ABI.
     */
    @Nullable
    public HashCode hashClassFile(FileDetails fileDetails) {
        HashCode signature = persistentCache.get(fileDetails.getContent().getContentMd5());
        if (signature != null) {
            return signature;
        }

        File file = new File(fileDetails.getPath());
        try {
            byte[] src = Files.toByteArray(file);
            Hasher hasher = createHasher();
            if (!hashClassBytes(hasher, src)) {
                return null;
            }
            signature = hasher.hash();
        } catch (Exception e) {
            throw new UncheckedIOException("Could not calculate the signature for class file " + file, e);
        }

        persistentCache.put(fileDetails.getContent().getContentMd5(), signature);
        return signature;
    }

    private boolean hashClassBytes(Hasher hasher, byte[] classBytes) {
        // Use the ABI as the hash
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());
        Java9ClassReader reader = new Java9ClassReader(classBytes);
        if (!extractor.shouldExtractApiClassFrom(reader)) {
            return false;
        }

        byte[] signature = extractor.extractApiClassFrom(reader);
        if (signature == null) {
            return false;
        }

        hasher.putBytes(signature);
        return true;
    }

    public HashCode hashJarFile(FileDetails fileDetails) {
        HashCode signature = persistentCache.get(fileDetails.getContent().getContentMd5());
        if (signature != null) {
            return signature;
        }

        File file = new File(fileDetails.getPath());
        final Hasher hasher = createHasher();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            // Ensure we visit the zip entries in a deterministic order
            Map<String, ZipEntry> entriesByName = new TreeMap<String, ZipEntry>();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".class")) {
                    entriesByName.put(zipEntry.getName(), zipEntry);
                }
            }
            for (ZipEntry zipEntry : entriesByName.values()) {
                visit(zipFile, zipEntry, hasher);
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Could not calculate the signature for Jar file " + file, e);
        } finally {
            IOUtils.closeQuietly(zipFile);
        }
        signature = hasher.hash();
        persistentCache.put(fileDetails.getContent().getContentMd5(), signature);
        return signature;
    }

    private Hasher createHasher() {
        Hasher hasher = Hashing.md5().newHasher();
        hasher.putBytes(SIGNATURE);
        return hasher;
    }

    private void visit(ZipFile zipFile, ZipEntry zipEntry, Hasher hasher) {
        InputStream inputStream = null;
        try {
            inputStream = zipFile.getInputStream(zipEntry);
            byte[] src = ByteStreams.toByteArray(inputStream);
            hashClassBytes(hasher, src);
        } catch (Exception e) {
            throw new UncheckedIOException("Could not calculate the signature for class file " + zipEntry.getName(), e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
}
