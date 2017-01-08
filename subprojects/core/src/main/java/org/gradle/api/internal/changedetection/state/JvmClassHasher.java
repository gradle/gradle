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
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.resource.TextResource;
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

public class JvmClassHasher implements FileHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(JvmClassHasher.class.getName(), Charsets.UTF_8).asBytes();
    private static final HashCode IGNORED = createHasher().hash();
    private final FileHasher delegate;

    public JvmClassHasher(FileHasher hasher) {
        this.delegate = hasher;
    }

    @Override
    public HashCode hash(TextResource resource) {
        return delegate.hash(resource);
    }

    @Override
    public HashCode hash(final File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".class")) {
            return hashClassFile(file);

        } else if (fileName.endsWith(".jar")) {
            return hashJarFile(file);
        }
        return IGNORED;
    }

    private HashCode hashClassFile(File file) {
        try {
            byte[] src = Files.toByteArray(file);
            Hasher hasher = createHasher();
            hashClassBytes(hasher, src);
            return hasher.hash();
        } catch (IOException e) {
            return delegate.hash(file);
        }
    }

    private static void hashClassBytes(Hasher hasher, byte[] classBytes) {
        // Use the ABI as the hash
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());
        Java9ClassReader reader = new Java9ClassReader(classBytes);
        if (extractor.shouldExtractApiClassFrom(reader)) {
            byte[] signature = extractor.extractApiClassFrom(reader);
            hasher.putBytes(signature);
        }
    }

    private static HashCode hashJarFile(File file) {
        final Hasher hasher = createHasher();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);
            HashingJarVisitor hashingJarVisitor = new HashingJarVisitor(zipFile, hasher);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            // Ensure we visit the zip entries in a deterministic order
            Map<String, ZipEntry> entriesByName = new TreeMap<String, ZipEntry>();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    entriesByName.put(zipEntry.getName(), zipEntry);
                }
            }
            for (ZipEntry zipEntry : entriesByName.values()) {
                hashingJarVisitor.execute(zipEntry);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(zipFile);
        }
        return hasher.hash();
    }

    private static Hasher createHasher() {
        final Hasher hasher = Hashing.md5().newHasher();
        hasher.putBytes(SIGNATURE);
        return hasher;
    }

    @Override
    public HashCode hash(FileTreeElement fileDetails) {
        return hash(fileDetails.getFile());
    }

    @Override
    public HashCode hash(File file, FileMetadataSnapshot fileDetails) {
        return hash(file);
    }

    private static class HashingJarVisitor implements Action<ZipEntry> {
        private final ZipFile zipFile;
        private final Hasher hasher;

        public HashingJarVisitor(ZipFile zipFile, Hasher hasher) {
            this.zipFile = zipFile;
            this.hasher = hasher;
        }

        public void execute(ZipEntry zipEntry) {
            if (zipEntry.getName().endsWith(".class")) {
                InputStream inputStream = null;
                try {
                    inputStream = zipFile.getInputStream(zipEntry);
                    byte[] src = ByteStreams.toByteArray(inputStream);
                    hashClassBytes(hasher, src);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                } finally {
                    try {
                        if (inputStream!=null) {
                            inputStream.close();
                        }
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }
        }
    }
}
