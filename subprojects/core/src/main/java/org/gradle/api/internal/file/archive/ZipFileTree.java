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
import org.gradle.api.file.LinksStrategy;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.provider.Provider;
import org.gradle.cache.internal.DecompressionCache;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.hash.FileHasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

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
    public void visit(FileVisitor visitor, LinksStrategy linksStrategy) {
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
                ZipVisitor zipVisitor = new ZipVisitor(zip, zipFile, expandedDir, visitor, stopFlag, linksStrategy.preserveLinks(), chmod);
                zipVisitor.visitAll();
            } catch (GradleException e) {
                throw e;
            } catch (Exception e) {
                throw new GradleException(format("Cannot expand %s.", getDisplayName()), e);
            }
        });
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

    private static final class ZipVisitor extends ArchiveVisitor<ZipArchiveEntry> {
        private final ZipFile zip;

        public ZipVisitor(ZipFile zip, File zipFile, File expandedDir, FileVisitor visitor, AtomicBoolean stopFlag, boolean preserveLinks, Chmod chmod) {
            super(zipFile, expandedDir, visitor, stopFlag, preserveLinks, chmod);
            this.zip = zip;
        }

        @Override
        protected TreeMap<String, ZipArchiveEntry> getEntries() {
            if (entries == null) {
                // The iteration order of zip.getEntries() is based on the hash of the zip entry. This isn't much use
                // to us. So, collect the entries in a map and iterate over them in alphabetical order.
                TreeMap<String, ZipArchiveEntry> entriesByName = new TreeMap<>();
                Enumeration<ZipArchiveEntry> allEntries = zip.getEntries();
                while (allEntries.hasMoreElements()) {
                    ZipArchiveEntry entry = allEntries.nextElement();
                    entriesByName.put(getPath(entry), entry);
                }
                entries = entriesByName;
            }
            return entries;
        }

        @Override
        boolean isSymlink(ZipArchiveEntry zipArchiveEntry) {
            return zipArchiveEntry.isUnixSymlink();
        }

        @Override
        boolean isDirectory(ZipArchiveEntry zipArchiveEntry) {
            return zipArchiveEntry.isDirectory();
        }

        @Override
        String getPath(ZipArchiveEntry zipArchiveEntry) {
            return zipArchiveEntry.getName();
        }

        @Override
        String getSymlinkTarget(ZipArchiveEntry entry) {
            try {
                return zip.getUnixSymlink(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @SuppressWarnings("OctalInteger")
        @Override
        int getUnixMode(ZipArchiveEntry zipArchiveEntry) {
            return zipArchiveEntry.getUnixMode() & 0777;
        }

        @Override
        long getLastModifiedTime(ZipArchiveEntry zipArchiveEntry) {
            return zipArchiveEntry.getLastModifiedDate().getTime();
        }

        @Override
        long getSize(ZipArchiveEntry zipArchiveEntry) {
            return zipArchiveEntry.getSize();
        }

        @Override
        @Nullable
        ZipArchiveEntry getEntry(String path) {
            return zip.getEntry(path);
        }

        @Override
        DetailsImpl createDetails(
            ZipArchiveEntry zipArchiveEntry,
            String targetPath
        ) {
            return new DetailsImpl(this, zipArchiveEntry, targetPath);
        }
    }

    private static final class DetailsImpl extends AbstractArchiveFileTreeElement<ZipArchiveEntry, ZipVisitor> {

        public DetailsImpl(
            ZipVisitor zipMetadata,
            ZipArchiveEntry entry,
            String targetPath
        ) {
            super(zipMetadata, entry, targetPath);
        }

        @Override
        public String getDisplayName() {
            return format("zip entry %s!%s", archiveMetadata.getOriginalFile(), entry.getName());
        }

        @Override
        public InputStream open() {
            if (!isLink() || getSymbolicLinkDetails().targetExists()) {
                try {
                    return archiveMetadata.zip.getInputStream(getResultEntry());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            throw new GradleException(String.format("Couldn't follow symbolic link '%s' pointing to '%s'.", getRelativePath(), getSymbolicLinkDetails().getTarget()));
        }
    }
}
