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
package org.gradle.api.internal.file.archive;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.internal.file.DefaultFilePermissions;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.cache.internal.DecompressionCache;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.internal.GFileUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class TarFileTree extends AbstractArchiveFileTree {
    private final Provider<File> tarFileProvider;
    private final Provider<ReadableResourceInternal> resource;
    private final Chmod chmod;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileHasher fileHasher;

    public TarFileTree(
            Provider<File> tarFileProvider,
            Provider<ReadableResourceInternal> resource,
            Chmod chmod,
            DirectoryFileTreeFactory directoryFileTreeFactory,
            FileHasher fileHasher,
            DecompressionCache decompressionCache) {
        super(decompressionCache);
        this.tarFileProvider = tarFileProvider;
        this.resource = resource;
        this.chmod = chmod;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileHasher = fileHasher;
    }

    @Override
    public String getDisplayName() {
        return String.format("TAR '%s'", resource.get().getDisplayName());
    }

    @Override
    public DirectoryFileTree getMirror() {
        return directoryFileTreeFactory.create(getExpandedDir());
    }

    @Override
    public void visit(FileVisitor visitor) {
        decompressionCache.useCache(() -> {
            InputStream inputStream;
            try {
                inputStream = new BufferedInputStream(resource.get().read());
            } catch (ResourceException e) {
                throw cannotExpand(e);
            }

            try {
                try {
                    Objects.requireNonNull(visitor);
                    visitImpl(visitor, inputStream);
                } finally {
                    inputStream.close();
                }
            } catch (Exception e) {
                String message = "Unable to expand " + getDisplayName() + "\n"
                        + "  The tar might be corrupted or it is compressed in an unexpected way.\n"
                        + "  By default the tar tree tries to guess the compression based on the file extension.\n"
                        + "  If you need to specify the compression explicitly please refer to the DSL reference.";
                throw new GradleException(message, e);
            }
        });
    }

    private void visitImpl(FileVisitor visitor, InputStream inputStream) throws IOException {
        checkFormat(inputStream);

        AtomicBoolean stopFlag = new AtomicBoolean();
        DetailsImpl.NoCloseTarArchiveInputStream tar = new DetailsImpl.NoCloseTarArchiveInputStream(inputStream);
        File expandedDir = getExpandedDir();
        ReadableResourceInternal resource = this.resource.get();
        TarArchiveEntry entry;
        while (!stopFlag.get() && (entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                visitor.visitDir(new DetailsImpl(resource, expandedDir, entry, tar, stopFlag, chmod));
            } else {
                visitor.visitFile(new DetailsImpl(resource, expandedDir, entry, tar, stopFlag, chmod));
            }
        }
    }

    @Override
    public Provider<File> getBackingFileProvider() {
        return tarFileProvider;
    }

    private File getExpandedDir() {
        File tarFile = tarFileProvider.get();
        HashCode fileHash = hashFile(tarFile);
        String expandedDirName = "tar_" + fileHash;
        return new File(decompressionCache.getBaseDir(), expandedDirName);
    }

    private HashCode hashFile(File tarFile) {
        try {
            return fileHasher.hash(tarFile);
        } catch (Exception e) {
            throw cannotExpand(e);
        }
    }

    private RuntimeException cannotExpand(Exception e) {
        throw new InvalidUserDataException(String.format("Cannot expand %s.", getDisplayName()), e);
    }

    /**
     * Using Apache Commons Compress to un-tar a non-tar archive fails silently, without any exception
     * or error, so we need a way of checking the format explicitly.
     *
     * This is a simplified version of <code>ArchiveStreamFactory.detect(InputStream)</code>,
     * and extended to not throw an exception for empty TAR files (i.e. ones with no entries in them).
     */
    private void checkFormat(InputStream inputStream) throws IOException {
        if (!inputStream.markSupported()) {
            throw new IOException("TAR input stream does not support mark/reset.");
        }

        int tarHeaderSize = 512; // ArchiveStreamFactory.TAR_HEADER_SIZE
        inputStream.mark(tarHeaderSize);
        final byte[] tarHeader = new byte[tarHeaderSize];
        int signatureLength = IOUtils.readFully(inputStream, tarHeader);
        inputStream.reset();
        if (TarArchiveInputStream.matches(tarHeader, signatureLength)) {
            return;
        }

        if (signatureLength >= tarHeaderSize) {
            try (TarArchiveInputStream tais = new TarArchiveInputStream(new ByteArrayInputStream(tarHeader))) {
                TarArchiveEntry tarEntry = tais.getNextTarEntry();
                if (tarEntry == null) {
                    // empty TAR
                    return;
                }
                if (tarEntry.isCheckSumOK()) {
                    return;
                }
            } catch (Exception e) {
                // can generate IllegalArgumentException as well as IOException
                // not a TAR ignored
            }
        }
        throw new IOException("Not a TAR archive");
    }

    private static final class DetailsImpl extends AbstractArchiveFileTreeElement {
        private final TarArchiveEntry entry;
        private final NoCloseTarArchiveInputStream tar;
        private final ReadableResourceInternal resource;
        private boolean read;

        public DetailsImpl(ReadableResourceInternal resource, File expandedDir, TarArchiveEntry entry, NoCloseTarArchiveInputStream tar, AtomicBoolean stopFlag, Chmod chmod) {
            super(chmod, expandedDir, stopFlag);
            this.resource = resource;
            this.entry = entry;
            this.tar = tar;
        }

        @Override
        public String getDisplayName() {
            return String.format("tar entry %s!%s", resource.getDisplayName(), entry.getName());
        }

        @Override
        public InputStream open() {
            if (read) {
                getFile();
                return GFileUtils.openInputStream(getFile());
            } else if (tar.getCurrentEntry() != entry) {
                throw new UnsupportedOperationException(String.format("The contents of %s has already been read.", this));
            } else {
                read = true;
                return tar;
            }
        }

        @Override
        public FilePermissions getPermissions() {
            return new DefaultFilePermissions(entry.getMode());
        }

        @Override
        protected String safeEntryName() {
            return entry.getName();
        }

        @Override
        protected TarArchiveEntry getArchiveEntry() {
            return entry;
        }

        private static final class NoCloseTarArchiveInputStream extends TarArchiveInputStream {
            public NoCloseTarArchiveInputStream(InputStream is) {
                super(is);
            }

            @Override
            public void close() {
            }
        }
    }
}
