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
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.LinksStrategy;
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

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.lang.String.format;
import static org.gradle.internal.file.PathTraversalChecker.safePathName;

public class TarFileTree extends AbstractArchiveFileTree {
    private final Provider<File> tarFileProvider;
    private final Provider<ReadableResourceInternal> resource;
    private final Chmod chmod;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileHasher fileHasher;
    private boolean hasMetadata = false;
    private final TreeMap<String, TarArchiveEntry> metadata = new TreeMap<>();
    private Set<String> linkTargets = null;

    public TarFileTree(
        Provider<File> tarFileProvider,
        Provider<ReadableResourceInternal> resource,
        Chmod chmod,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        FileHasher fileHasher,
        DecompressionCache decompressionCache
    ) {
        super(decompressionCache);
        this.tarFileProvider = tarFileProvider;
        this.chmod = chmod;
        this.resource = resource;
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
    public void visit(FileVisitor visitor, LinksStrategy linksStrategy) {
        decompressionCache.useCache(() -> {
            try {
                Objects.requireNonNull(visitor);
            } catch (NullPointerException e) {
                throw cannotExpand(e);
            }
            AtomicBoolean stopFlag = new AtomicBoolean();

            final boolean needsLinkPostProcessing;
            final boolean needsLinkFollowing;
            if (linksStrategy == LinksStrategy.PRESERVE_ALL) {
                needsLinkPostProcessing = false;
                needsLinkFollowing = false;
            } else if (linksStrategy == LinksStrategy.PRESERVE_RELATIVE || linksStrategy == LinksStrategy.ERROR) {
                needsLinkPostProcessing = !hasMetadata;
                needsLinkFollowing = false;
            } else {
                needsLinkPostProcessing = true;
                needsLinkFollowing = true;
            }

            // Metadata is needed ahead of time to know which files we need to extract to get the link targets
            if (needsLinkFollowing && !hasMetadata) {
                withStream(true, tar -> {
                    try {
                        TarArchiveEntry entry;
                        while ((entry = tar.getNextTarEntry()) != null) {
                            metadata.put(safePathName(entry.getName()), entry);
                        }
                    } catch (IOException e) {
                        throw cannotExpand(e);
                    }
                });
                hasMetadata = true;
            }

            withStream(!hasMetadata, tar -> {
                File expandedDir = getExpandedDir();
                try {
                    TarVisitor tarVisitor = new TarVisitor(
                        tar, tarFileProvider.get(), expandedDir, metadata,
                        needsLinkPostProcessing, needsLinkFollowing, linkTargets,
                        visitor, stopFlag, linksStrategy.preserveLinks(), chmod
                    );
                    tarVisitor.visitAll();
                    if (linkTargets == null && !stopFlag.get()) {
                        linkTargets = tarVisitor.getAllLinkTargets();
                    }
                    hasMetadata = !stopFlag.get();
                } catch (IOException e) {
                    throw cannotExpand(e);
                }
            });
        });
    }

    private void withStream(boolean checkStream, Consumer<NoCloseTarArchiveInputStream> action) {
        InputStream inputStream;
        try {
            inputStream = new BufferedInputStream(resource.get().read());
        } catch (ResourceException e) {
            throw cannotExpand(e);
        }

        NoCloseTarArchiveInputStream tar = new NoCloseTarArchiveInputStream(inputStream);
        try {
            if (checkStream) {
                checkFormat(inputStream);
            }
            try {
                action.accept(tar);
            } finally {
                inputStream.close();
            }
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            String message = "Unable to expand " + getDisplayName() + "\n"
                + "  The tar might be corrupted or it is compressed in an unexpected way.\n"
                + "  By default the tar tree tries to guess the compression based on the file extension.\n"
                + "  If you need to specify the compression explicitly please refer to the DSL reference.";
            throw new GradleException(message, e);
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
        int signatureLength = org.apache.commons.compress.utils.IOUtils.readFully(inputStream, tarHeader);
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

    private static final class TarVisitor extends ArchiveVisitor<TarArchiveEntry> {
        private final NoCloseTarArchiveInputStream tar;
        boolean isStreaming = true;
        private final TreeMap<String, TarArchiveEntry> metadata;
        private final boolean needsLinkPostProcessing;
        private final boolean needsLinkFollowing;
        @Nullable
        private Set<String> linkTargets;

        public TarVisitor(
            NoCloseTarArchiveInputStream tar,
            File tarFile,
            File expandedDir,
            TreeMap<String, TarArchiveEntry> metadata,
            boolean needsLinkPostProcessing, //FIXME
            boolean needsLinkFollowing,
            @Nullable Set<String> linkTargets,
            FileVisitor visitor,
            AtomicBoolean stopFlag,
            boolean preserveLinks, //FIXME
            Chmod chmod
        ) {
            super(tarFile, expandedDir, visitor, stopFlag, preserveLinks, chmod);
            this.tar = tar;
            this.metadata = metadata;
            this.needsLinkPostProcessing = needsLinkPostProcessing;
            this.needsLinkFollowing = needsLinkFollowing;
            this.linkTargets = linkTargets;
        }

        @Override
        protected TreeMap<String, TarArchiveEntry> getEntries() {
            return metadata; //NOTE: may be partially filled
        }

        @Nullable
        Set<String> getAllLinkTargets() {
            if (linkTargets == null && needsLinkFollowing) {
                linkTargets = new HashSet<>();
                for (TarArchiveEntry entry : metadata.values()) {
                    if (!entry.isSymbolicLink()) {
                        continue;
                    }
                    TarArchiveEntry target = getTargetEntry(entry);
                    if (target == null || target.isSymbolicLink()) {
                        continue;
                    }
                    if (target.isFile()) {
                        linkTargets.add(target.getName());
                    } else {
                        String originalPath = target.getName();
                        String current = originalPath;
                        while (true) {
                            Map.Entry<String, TarArchiveEntry> subEntry = metadata.higherEntry(current);
                            if (subEntry != null && subEntry.getKey().startsWith(originalPath)) {
                                current = subEntry.getKey();
                            } else {
                                break;
                            }
                            if (subEntry.getValue().isFile()) {
                                linkTargets.add(current);
                            }
                        }
                    }
                }
            }
            return linkTargets;
        }

        @Override
        public void visitAll() throws IOException {
            TarArchiveEntry entry;
            List<TarArchiveEntry> linksQueue = needsLinkPostProcessing ? new ArrayList<>() : null;

            while (!stopFlag.get() && (entry = (TarArchiveEntry) tar.getNextEntry()) != null) {
                metadata.putIfAbsent(safePathName(entry.getName()), entry);
                if (needsLinkPostProcessing && entry.isSymbolicLink()) {
                    linksQueue.add(entry);
                } else {
                    boolean extract = needsLinkFollowing && entry.isFile() && getAllLinkTargets().contains(entry.getName());
                    visitEntry(entry, entry.getName(), extract);
                }
            }
            isStreaming = false;

            // postprocessing links because we need metadata for the link following and copying, and we can't get it while streaming
            if (linksQueue != null && !linksQueue.isEmpty()) {
                for (TarArchiveEntry link : linksQueue) {
                    if (stopFlag.get()) {
                        break;
                    }
                    visitEntry(link, link.getName(), false);
                }
            }
        }

        @Override
        boolean isSymlink(TarArchiveEntry tarArchiveEntry) {
            return tarArchiveEntry.isSymbolicLink();
        }

        @Override
        boolean isDirectory(TarArchiveEntry tarArchiveEntry) {
            return tarArchiveEntry.isDirectory();
        }

        @Override
        String getPath(TarArchiveEntry tarArchiveEntry) {
            return tarArchiveEntry.getName();
        }

        @Override
        String getSymlinkTarget(TarArchiveEntry entry) {
            return entry.getLinkName();
        }

        @SuppressWarnings("OctalInteger")
        @Override
        int getUnixMode(TarArchiveEntry tarArchiveEntry) {
            return tarArchiveEntry.getMode() & 0777;
        }

        @Override
        long getLastModifiedTime(TarArchiveEntry tarArchiveEntry) {
            return tarArchiveEntry.getLastModifiedDate().getTime();
        }

        @Override
        long getSize(TarArchiveEntry tarArchiveEntry) {
            return tarArchiveEntry.getSize();
        }

        @Override
        @Nullable
        TarArchiveEntry getEntry(String path) {
            return metadata.get(path);
        }

        @Override
        DetailsImpl createDetails(
            TarArchiveEntry tarArchiveEntry,
            String targetPath
        ) {
            return new DetailsImpl(this, tarArchiveEntry, targetPath);
        }
    }

    private static final class DetailsImpl extends AbstractArchiveFileTreeElement<TarArchiveEntry, TarVisitor> {
        private boolean read = false;

        public DetailsImpl(
            TarVisitor tarMetadata,
            TarArchiveEntry entry,
            String targetPath
        ) {
            super(tarMetadata, entry, targetPath);
        }

        @Override
        public String getDisplayName() {
            return format("tar entry %s!%s", archiveMetadata.getOriginalFile(), entry.getName());
        }

        @Override
        public InputStream open() {
            if (!isLink() && !read && archiveMetadata.isStreaming) {
                read = true;
                return archiveMetadata.tar;
            }

            if (!isLink() || getSymbolicLinkDetails().targetExists()) {
                File unpackedTarget = new File(archiveMetadata.expandedDir, getResultEntry().getName());
                return GFileUtils.openInputStream(unpackedTarget);
            }

            throw new GradleException(String.format("Couldn't follow symbolic link '%s' pointing to '%s'.", getRelativePath(), getSymbolicLinkDetails().getTarget()));
        }
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
