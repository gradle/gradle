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
package org.gradle.api.internal.file.archive;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.provider.Provider;
import org.gradle.cache.internal.DecompressionCache;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static org.gradle.util.internal.ZipSlip.safeZipEntryName;

public class ZipFileTree extends AbstractArchiveFileTree {
    private final Provider<File> fileProvider;
    private final Chmod chmod;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileHasher fileHasher;

    public ZipFileTree(
        Provider<File> zipFile,
        Chmod chmod,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        FileHasher fileHasher,
        DecompressionCache decompressionCache
    ) {
        super(decompressionCache);
        this.fileProvider = zipFile;
        this.chmod = chmod;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileHasher = fileHasher;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return format("ZIP '%s'", fileProvider.getOrNull());
    }

    @Override
    public DirectoryFileTree getMirror() {
        return directoryFileTreeFactory.create(getExpandedDir());
    }

    @Override
    public void visit(FileVisitor visitor) {
        decompressionCache.useCache(() -> {
            File zipFile = fileProvider.get();
            if (!zipFile.exists()) {
                throw new InvalidUserDataException(format("Cannot expand %s as it does not exist.", getDisplayName()));
            }
            if (!zipFile.isFile()) {
                throw new InvalidUserDataException(format("Cannot expand %s as it is not a file.", getDisplayName()));
            }

            AtomicBoolean stopFlag = new AtomicBoolean();
            File expandedDir = getExpandedDir();
            try (ZipFile zip = new ZipFile(zipFile)) {
                // The iteration order of zip.getEntries() is based on the hash of the zip entry. This isn't much use
                // to us. So, collect the entries in a map and iterate over them in alphabetical order.
                Iterator<ZipArchiveEntry> sortedEntries = entriesSortedByName(zip);
                while (!stopFlag.get() && sortedEntries.hasNext()) {
                    ZipArchiveEntry entry = sortedEntries.next();
                    DetailsImpl details = new DetailsImpl(zipFile, expandedDir, entry, zip, stopFlag, chmod);
                    if (entry.isDirectory()) {
                        visitor.visitDir(details);
                    } else {
                        visitor.visitFile(details);
                    }
                }
            } catch (Exception e) {
                throw new GradleException(format("Cannot expand %s.", getDisplayName()), e);
            }
        });
    }

    private Iterator<ZipArchiveEntry> entriesSortedByName(ZipFile zip) {
        Map<String, ZipArchiveEntry> entriesByName = new TreeMap<>();
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            entriesByName.put(entry.getName(), entry);
        }
        return entriesByName.values().iterator();
    }

    @Override
    public Provider<File> getBackingFileProvider() {
        return fileProvider;
    }

    private File getExpandedDir() {
        File zipFile = fileProvider.get();
        String expandedDirName = "zip_" + fileHasher.hash(zipFile);
        return new File(decompressionCache.getBaseDir(), expandedDirName);
    }

    private static final class DetailsImpl extends AbstractArchiveFileTreeElement {
        private final File originalFile;
        private final ZipArchiveEntry entry;
        private final ZipFile zip;

        public DetailsImpl(File originalFile, File expandedDir, ZipArchiveEntry entry, ZipFile zip, AtomicBoolean stopFlag, Chmod chmod) {
            super(chmod, expandedDir, stopFlag);
            this.originalFile = originalFile;
            this.entry = entry;
            this.zip = zip;
        }

        @Override
        public String getDisplayName() {
            return format("zip entry %s!%s", originalFile, entry.getName());
        }

        @Override
        protected String safeEntryName() {
            return safeZipEntryName(entry.getName());
        }

        @Override
        protected ZipArchiveEntry getArchiveEntry() {
            return entry;
        }

        @Override
        public InputStream open() {
            try {
                return zip.getInputStream(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public int getMode() {
            int unixMode = entry.getUnixMode() & 0777;
            if (unixMode != 0) {
                return unixMode;
            }
            //no mode infos available - fall back to defaults
            return isDirectory()
                ? FileSystem.DEFAULT_DIR_MODE
                : FileSystem.DEFAULT_FILE_MODE;
        }
    }
}
