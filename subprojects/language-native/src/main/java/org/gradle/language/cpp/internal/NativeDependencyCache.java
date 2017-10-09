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

package org.gradle.language.cpp.internal;

import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This is intended to be temporary, until more metadata can be published and the dependency resolution engine can deal with it. As such, it's not particularly performant or robust.
 */
public class NativeDependencyCache implements Stoppable {
    private final PersistentCache cache;
    private final FileHasher fileHasher;

    public NativeDependencyCache(CacheRepository cacheRepository, FileHasher fileHasher) {
        cache = cacheRepository.cache("native-dep").withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.None)).open();
        this.fileHasher = fileHasher;
    }

    /**
     * Returns the directory containing the unzipped headers from the given zip.
     */
    public File getUnpackedHeaders(final File headersZip, final String baseName) {
        final String hash = HashUtil.compactStringFor(fileHasher.hash(headersZip));
        return cache.useCache(new Factory<File>() {
            @Override
            public File create() {
                File dir = new File(cache.getBaseDir(), hash + "/" + baseName);
                if (dir.isDirectory()) {
                    return dir;
                }
                try {
                    unzipTo(headersZip, dir);
                } catch (IOException e) {
                    // Intentionally doesn't clean up on failure
                    throw new UncheckedIOException("Could not unzip headers from " + headersZip, e);
                }
                return dir;
            }
        });
    }

    private void unzipTo(File headersZip, File unzipDir) throws IOException {
        ZipInputStream inputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(headersZip)));
        try {
            ZipEntry entry = null;
            while ((entry = inputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File outFile = new File(unzipDir, entry.getName());
                Files.createParentDirs(outFile);
                FileOutputStream outputStream = new FileOutputStream(outFile);
                try {
                    IOUtils.copyLarge(inputStream, outputStream);
                } finally {
                    outputStream.close();
                }
            }
        } finally {
            inputStream.close();
        }
    }

    @Override
    public void stop() {
        cache.close();
    }
}
