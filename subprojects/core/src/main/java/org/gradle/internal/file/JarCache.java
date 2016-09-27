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

package org.gradle.internal.file;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class JarCache {
    private final Lock lock = new ReentrantLock();
    private final Cache<File, FileInfo> cachedFiles;

    public JarCache() {
        this.cachedFiles = CacheBuilder.newBuilder().maximumSize(200).build();
    }

    /**
     * Returns a cached copy of the given file. The cached copy is guaranteed to not be modified or removed.
     *
     * @param original The source file.
     * @param baseDirFactory A factory that can provide a base directory for the file cache.
     * @return The cached file.
     */
    public File getCachedJar(File original, Factory<File> baseDirFactory) {
        File source = FileUtils.canonicalize(original);
        FileInfo fileInfo;
        // TODO - do some of this work concurrently
        lock.lock();
        try {
            fileInfo = cachedFiles.getIfPresent(source);
            if (fileInfo == null || !fileInfo.cachedFile.exists()) {
                // TODO - use the hashing service for this
                long lastModified = source.lastModified();
                long length = source.length();
                HashValue hashValue = HashUtil.createHash(source, "sha1");
                fileInfo = copyIntoCache(baseDirFactory, source, lastModified, length, hashValue);
            } else {
                // TODO - use the change detection service for this
                long lastModified = source.lastModified();
                long length = source.length();
                if (lastModified != fileInfo.lastModified || length != fileInfo.length) {
                    HashValue hashValue = HashUtil.createHash(source, "sha1");
                    if (!hashValue.equals(fileInfo.hashValue)) {
                        fileInfo = copyIntoCache(baseDirFactory, source, lastModified, length, hashValue);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return fileInfo.cachedFile;
    }

    private FileInfo copyIntoCache(Factory<File> baseDirFactory, File source, long lastModified, long length, HashValue hashValue) {
        File baseDir = baseDirFactory.create();
        File cachedFile = new File(baseDir, hashValue.asCompactString() + '/' + source.getName());
        if (!cachedFile.isFile()) {
            // Has previously been added to the cache directory
            GFileUtils.copyFile(source, cachedFile);
        }
        FileInfo fileInfo = new FileInfo(lastModified, length, hashValue, cachedFile);
        cachedFiles.put(source, fileInfo);
        return fileInfo;
    }

    private static class FileInfo {
        final long lastModified;
        final long length;
        final HashValue hashValue;
        final File cachedFile;

        private FileInfo(long lastModified, long length, HashValue hashValue, File cachedFile) {
            this.lastModified = lastModified;
            this.length = length;
            this.hashValue = hashValue;
            this.cachedFile = cachedFile;
        }
    }
}
