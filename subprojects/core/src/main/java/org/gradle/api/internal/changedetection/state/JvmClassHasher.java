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
import org.gradle.util.DeprecationLogger;
import org.gradle.util.internal.Java9ClassReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JvmClassHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(JvmClassHasher.class.getName(), Charsets.UTF_8).asBytes();
    private static final HashCode MALFORMED_JAR = Hashing.md5().hashString(JvmClassHasher.class.getName() + " : malformed jar", Charsets.UTF_8);

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
        Hasher hasher = createHasher();
        byte[] src;
        try {
            src = Files.toByteArray(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            if (!hashClassBytes(hasher, src)) {
                return null;
            }
            signature = hasher.hash();
        } catch (Exception e) {
            signature = hasher.putBytes(src).hash();
            DeprecationLogger.nagUserWith("Malformed class file [" + file.getName() + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
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
            signature = MALFORMED_JAR;
            DeprecationLogger.nagUserWith("Malformed jar [" + file.getName() + "] found on compile classpath. Gradle 5.0 will no longer allow malformed jars on compile classpath.");
        } finally {
            IOUtils.closeQuietly(zipFile);
        }
        signature = signature != null ? signature : hasher.hash();
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
        byte[] src = new byte[0];
        try {
            inputStream = zipFile.getInputStream(zipEntry);
            try {
                src = ByteStreams.toByteArray(inputStream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            hashClassBytes(hasher, src);
        } catch (Exception e) {
            hasher.putBytes(src);
            DeprecationLogger.nagUserWith("Malformed class file [" + zipEntry.getName() + "] in jar [" + zipFile.getName() + "] found on classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }
}
