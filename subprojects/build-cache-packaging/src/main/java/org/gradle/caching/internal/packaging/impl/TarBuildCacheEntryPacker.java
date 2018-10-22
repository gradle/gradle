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

package org.gradle.caching.internal.packaging.impl;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.origin.OriginReader;
import org.gradle.caching.internal.origin.OriginWriter;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.internal.packaging.CacheableTree;
import org.gradle.internal.IoActions;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathStringTracker;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.caching.internal.packaging.impl.PackerDirectoryUtil.ensureDirectoryForTree;
import static org.gradle.caching.internal.packaging.impl.PackerDirectoryUtil.makeDirectory;

/**
 * Packages build cache entries to a POSIX TAR file.
 */
public class TarBuildCacheEntryPacker implements BuildCacheEntryPacker {
    @SuppressWarnings("OctalInteger")
    private interface UnixPermissions {
        int FILE_FLAG =         0100000;
        int DEFAULT_FILE_PERM =    0644;
        int DIR_FLAG =           040000;
        int DEFAULT_DIR_PERM =     0755;
        int PERM_MASK           = 07777;
    }

    private static final String METADATA_PATH = "METADATA";
    private static final Pattern TREE_PATH = Pattern.compile("(missing-)?tree-([^/]+)(?:/(.*))?");
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final ThreadLocal<byte[]> COPY_BUFFERS = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[BUFFER_SIZE];
        }
    };

    private final FileSystem fileSystem;
    private final StreamHasher streamHasher;
    private final StringInterner stringInterner;

    public TarBuildCacheEntryPacker(FileSystem fileSystem, StreamHasher streamHasher, StringInterner stringInterner) {
        this.fileSystem = fileSystem;
        this.streamHasher = streamHasher;
        this.stringInterner = stringInterner;
    }

    @Override
    public PackResult pack(SortedSet<? extends CacheableTree> trees, Map<String, CurrentFileCollectionFingerprint> fingerprints, OutputStream output, OriginWriter writeOrigin) throws IOException {
        BufferedOutputStream bufferedOutput;
        if (output instanceof BufferedOutputStream) {
            bufferedOutput = (BufferedOutputStream) output;
        } else {
            bufferedOutput = new BufferedOutputStream(output);
        }
        try (TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(bufferedOutput, "utf-8")) {
            tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOutput.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tarOutput.setAddPaxHeadersForNonAsciiNames(true);
            packMetadata(writeOrigin, tarOutput);
            long entryCount = pack(trees, fingerprints, tarOutput);
            return new PackResult(entryCount + 1);
        }
    }

    private void packMetadata(OriginWriter writeMetadata, TarArchiveOutputStream tarOutput) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeMetadata.execute(baos);
        createTarEntry(METADATA_PATH, baos.size(), UnixPermissions.FILE_FLAG | UnixPermissions.DEFAULT_FILE_PERM, tarOutput);
        tarOutput.write(baos.toByteArray());
        tarOutput.closeArchiveEntry();
    }

    private long pack(Collection<? extends CacheableTree> trees, Map<String, CurrentFileCollectionFingerprint> fingerprints, TarArchiveOutputStream tarOutput) {
        long entries = 0;
        for (CacheableTree tree : trees) {
            String treeName = tree.getName();
            CurrentFileCollectionFingerprint fingerprint = fingerprints.get(treeName);
            try {
                entries += packTree(tree, fingerprint, tarOutput);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack tree '%s': %s", treeName, ex.getMessage()), ex);
            }
        }
        return entries;
    }

    private long packTree(CacheableTree tree, CurrentFileCollectionFingerprint fingerprint, TarArchiveOutputStream tarOutput) {
        File root = tree.getRoot();
        if (root == null) {
            return 0;
        }
        PackingVisitor packingVisitor = new PackingVisitor(tarOutput, tree.getName(), tree.getType(), fileSystem);
        fingerprint.accept(packingVisitor);
        return packingVisitor.finish();
    }

    private static void createTarEntry(String path, long size, int mode, TarArchiveOutputStream tarOutput) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(path, true);
        entry.setSize(size);
        entry.setMode(mode);
        tarOutput.putArchiveEntry(entry);
    }

    @Override
    public UnpackResult unpack(SortedSet<? extends CacheableTree> trees, InputStream input, OriginReader readOrigin) throws IOException {
        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(input)) {
            return unpack(trees, tarInput, readOrigin);
        }
    }

    private UnpackResult unpack(SortedSet<? extends CacheableTree> trees, TarArchiveInputStream tarInput, OriginReader readOriginAction) throws IOException {
        Map<String, ? extends CacheableTree> treesByName = Maps.uniqueIndex(trees, new Function<CacheableTree, String>() {
            @Override
            public String apply(@Nullable CacheableTree tree) {
                assert tree != null;
                return tree.getName();
            }
        });
        TarArchiveEntry tarEntry;
        OriginMetadata originMetadata = null;
        Map<String, FileSystemLocationSnapshot> snapshots = new HashMap<String, FileSystemLocationSnapshot>();

        tarEntry = tarInput.getNextTarEntry();
        AtomicInteger entries = new AtomicInteger(0);
        while (tarEntry != null) {
            entries.incrementAndGet();
            String path = tarEntry.getName();

            if (path.equals(METADATA_PATH)) {
                // handle origin metadata
                originMetadata = readOriginAction.execute(new CloseShieldInputStream(tarInput));
                tarEntry = tarInput.getNextTarEntry();
            } else {
                // handle tree
                Matcher matcher = TREE_PATH.matcher(path);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Cached entry format error, invalid contents: " + path);
                }

                String treeName = unescape(matcher.group(2));
                CacheableTree tree = treesByName.get(treeName);
                if (tree == null) {
                    throw new IllegalStateException(String.format("No tree '%s' registered", treeName));
                }

                boolean missing = matcher.group(1) != null;
                String childPath = matcher.group(3);
                tarEntry = unpackTree(tree, tarInput, tarEntry, childPath, missing, snapshots, entries);
            }
        }
        if (originMetadata == null) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }

        return new UnpackResult(originMetadata, entries.get(), snapshots);
    }

    @Nullable
    private TarArchiveEntry unpackTree(CacheableTree tree, TarArchiveInputStream input, TarArchiveEntry rootEntry, String childPath, boolean missing, Map<String, FileSystemLocationSnapshot> snapshots, AtomicInteger entries) throws IOException {
        File treeRoot = tree.getRoot();
        String treeName = tree.getName();
        if (treeRoot == null) {
            throw new IllegalStateException("Optional tree should have a value: " + treeName);
        }

        boolean isDirEntry = rootEntry.isDirectory();
        boolean root = Strings.isNullOrEmpty(childPath);
        if (!root) {
            throw new IllegalStateException("Root needs to be the first entry in a tree");
        }
        // We are handling the root of the tree here
        if (missing) {
            unpackMissingFile(treeRoot);
            return input.getNextTarEntry();
        }

        CacheableTree.Type type = tree.getType();

        ensureDirectoryForTree(type, treeRoot);
        if (type == CacheableTree.Type.FILE) {
            if (isDirEntry) {
                throw new IllegalStateException("Should be a file: " + treeName);
            }
            RegularFileSnapshot fileSnapshot = unpackFile(input, rootEntry, treeRoot, treeRoot.getName());
            snapshots.put(treeName, fileSnapshot);
            return input.getNextTarEntry();
        }

        if (!isDirEntry) {
            throw new IllegalStateException("Should be a directory: " + treeName);
        }
        chmodUnpackedFile(rootEntry, treeRoot);

        return unpackDirectoryTree(input, rootEntry, snapshots, entries, treeRoot, treeName);
    }

    private void unpackMissingFile(File treeRoot) throws IOException {
        if (!makeDirectory(treeRoot.getParentFile())) {
            // Make sure tree is removed if it exists already
            if (treeRoot.exists()) {
                FileUtils.forceDelete(treeRoot);
            }
        }
    }

    private RegularFileSnapshot unpackFile(TarArchiveInputStream input, TarArchiveEntry entry, File file, String fileName) throws IOException {
        OutputStream output = new FileOutputStream(file);
        HashCode hash;
        try {
            hash = streamHasher.hashCopy(input, output);
            chmodUnpackedFile(entry, file);
        } finally {
            IoActions.closeQuietly(output);
        }
        String internedAbsolutePath = stringInterner.intern(file.getAbsolutePath());
        String internedFileName = stringInterner.intern(fileName);
        return new RegularFileSnapshot(internedAbsolutePath, internedFileName, hash, file.lastModified());
    }

    @Nullable
    private TarArchiveEntry unpackDirectoryTree(TarArchiveInputStream input, TarArchiveEntry rootEntry, Map<String, FileSystemLocationSnapshot> snapshots, AtomicInteger entries, File treeRoot, String treeName) throws IOException {
        RelativePathParser parser = new RelativePathParser();
        parser.rootPath(rootEntry.getName());

        MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.noSortingRequired();
        String rootPath = stringInterner.intern(treeRoot.getAbsolutePath());
        String rootDirName = stringInterner.intern(treeRoot.getName());
        builder.preVisitDirectory(rootPath, rootDirName);

        TarArchiveEntry entry;

        while ((entry = input.getNextTarEntry()) != null) {
            entries.incrementAndGet();
            boolean isDir = entry.isDirectory();
            int directoriesLeft = parser.nextPath(entry.getName(), isDir);
            for (int i = 0; i < directoriesLeft; i++) {
                builder.postVisitDirectory();
            }
            if (parser.getDepth() == 0) {
                break;
            }

            File file = new File(treeRoot, parser.getRelativePath());
            if (isDir) {
                FileUtils.forceMkdir(file);
                chmodUnpackedFile(entry, file);
                String internedAbsolutePath = stringInterner.intern(file.getAbsolutePath());
                String indernedDirName = stringInterner.intern(parser.getName());
                builder.preVisitDirectory(internedAbsolutePath, indernedDirName);
            } else {
                RegularFileSnapshot fileSnapshot = unpackFile(input, entry, file, parser.getName());
                builder.visit(fileSnapshot);
            }
        }

        for (int i = 0; i < parser.getDepth(); i++) {
            builder.postVisitDirectory();
        }

        snapshots.put(treeName, builder.getResult());
        return entry;
    }

    private void chmodUnpackedFile(TarArchiveEntry entry, File file) {
        fileSystem.chmod(file, entry.getMode() & UnixPermissions.PERM_MASK);
    }

    private static String escape(String name) {
        try {
            return URLEncoder.encode(name, "utf-8");
        } catch (UnsupportedEncodingException ignored) {
            throw new AssertionError();
        }
    }

    private static String unescape(String name) {
        try {
            return URLDecoder.decode(name, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static class PackingVisitor implements FileSystemSnapshotVisitor {
        private final RelativePathStringTracker relativePathStringTracker;
        private final TarArchiveOutputStream tarOutput;
        private final String treePath;
        private final String treeRoot;
        private final FileSystem fileSystem;
        private final CacheableTree.Type type;

        private long entries;

        public PackingVisitor(TarArchiveOutputStream tarOutput, String treeName, CacheableTree.Type type, FileSystem fileSystem) {
            this.tarOutput = tarOutput;
            this.treePath = "tree-" + escape(treeName);
            this.treeRoot = treePath + "/";
            this.type = type;
            this.fileSystem = fileSystem;
            this.relativePathStringTracker = new RelativePathStringTracker();
        }

        @Override
        public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
            boolean root = relativePathStringTracker.isRoot();
            relativePathStringTracker.enter(directorySnapshot);
            assertCorrectType(root, directorySnapshot);
            String targetPath = getTargetPath(root);
            int mode = root ? UnixPermissions.DEFAULT_DIR_PERM : fileSystem.getUnixMode(new File(directorySnapshot.getAbsolutePath()));
            storeDirectoryEntry(targetPath, mode, tarOutput);
            entries++;
            return true;
        }

        @Override
        public void visit(FileSystemLocationSnapshot fileSnapshot) {
            boolean root = relativePathStringTracker.isRoot();
            relativePathStringTracker.enter(fileSnapshot);
            String targetPath = getTargetPath(root);
            if (fileSnapshot.getType() == FileType.Missing) {
                storeMissingTree(targetPath, tarOutput);
            } else {
                assertCorrectType(root, fileSnapshot);
                File file = new File(fileSnapshot.getAbsolutePath());
                int mode = fileSystem.getUnixMode(file);
                storeFileEntry(file, targetPath, file.length(), mode, tarOutput);
            }
            relativePathStringTracker.leave();
            entries++;
        }

        @Override
        public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
            relativePathStringTracker.leave();
        }

        public long finish() {
            if (entries == 0) {
                storeMissingTree(treePath, tarOutput);
                entries++;
            }
            return entries;
        }

        private void assertCorrectType(boolean root, FileSystemLocationSnapshot snapshot) {
            if (root) {
                switch (type) {
                    case DIRECTORY:
                        if (snapshot.getType() != FileType.Directory) {
                            throw new IllegalArgumentException(String.format("Expected '%s' to be a directory", snapshot.getAbsolutePath()));
                        }
                        break;
                    case FILE:
                        if (snapshot.getType() != FileType.RegularFile) {
                            throw new IllegalArgumentException(String.format("Expected '%s' to be a file", snapshot.getAbsolutePath()));
                        }
                        break;
                    default:
                        throw new AssertionError();
                }
            }
        }

        private String getTargetPath(boolean root) {
            if (root) {
                return treePath;
            }
            String relativePath = relativePathStringTracker.getRelativePathString();
            return treeRoot + relativePath;
        }

        private void storeMissingTree(String treePath, TarArchiveOutputStream tarOutput) {
            try {
                createTarEntry("missing-" + treePath, 0, UnixPermissions.FILE_FLAG | UnixPermissions.DEFAULT_FILE_PERM, tarOutput);
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        private void storeDirectoryEntry(String path, int mode, TarArchiveOutputStream tarOutput) {
            try {
                createTarEntry(path + "/", 0, UnixPermissions.DIR_FLAG | mode, tarOutput);
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void storeFileEntry(File inputFile, String path, long size, int mode, TarArchiveOutputStream tarOutput) {
            try {
                createTarEntry(path, size, UnixPermissions.FILE_FLAG | mode, tarOutput);
                FileInputStream input = new FileInputStream(inputFile);
                try {
                    IOUtils.copyLarge(input, tarOutput, COPY_BUFFERS.get());
                } finally {
                    IoActions.closeQuietly(input);
                }
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
