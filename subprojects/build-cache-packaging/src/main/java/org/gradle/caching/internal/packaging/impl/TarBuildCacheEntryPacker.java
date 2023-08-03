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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.io.CountingOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.origin.OriginReader;
import org.gradle.caching.internal.origin.OriginWriter;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.internal.RelativePathSupplier;
import org.gradle.internal.file.BufferProvider;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.DirectorySnapshotBuilder;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.RelativePathTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.util.internal.PathTraversalChecker;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT;
import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS;

/**
 * Packages build cache entries to a POSIX TAR file.
 */
public class TarBuildCacheEntryPacker implements BuildCacheEntryPacker {

    @SuppressWarnings("OctalInteger")
    private interface UnixPermissions {
        int FILE_FLAG = 0100000;
        int DEFAULT_FILE_PERM = 0644;
        int DIR_FLAG = 040000;
        int DEFAULT_DIR_PERM = 0755;
        int PERM_MASK = 07777;
    }

    private static final Charset ENCODING = StandardCharsets.UTF_8;
    private static final String METADATA_PATH = "METADATA";
    private static final Pattern TREE_PATH = Pattern.compile("(missing-)?tree-([^/]+)(?:/(.*))?");

    private final TarPackerFileSystemSupport fileSystemSupport;
    private final FilePermissionAccess filePermissionAccess;
    private final StreamHasher streamHasher;
    private final Interner<String> stringInterner;
    private final BufferProvider bufferProvider;

    public TarBuildCacheEntryPacker(
        TarPackerFileSystemSupport fileSystemSupport,
        FilePermissionAccess filePermissionAccess,
        StreamHasher streamHasher,
        Interner<String> stringInterner,
        BufferProvider bufferProvider
    ) {
        this.fileSystemSupport = fileSystemSupport;
        this.filePermissionAccess = filePermissionAccess;
        this.streamHasher = streamHasher;
        this.stringInterner = stringInterner;
        this.bufferProvider = bufferProvider;
    }

    @Override
    public PackResult pack(CacheableEntity entity, Map<String, ? extends FileSystemSnapshot> snapshots, OutputStream output, OriginWriter writeOrigin) throws IOException {
        BufferedOutputStream bufferedOutput;
        if (output instanceof BufferedOutputStream) {
            bufferedOutput = (BufferedOutputStream) output;
        } else {
            bufferedOutput = new BufferedOutputStream(output);
        }
        try (TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(bufferedOutput, ENCODING.name())) {
            tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOutput.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tarOutput.setAddPaxHeadersForNonAsciiNames(true);
            packMetadata(writeOrigin, tarOutput);
            long entryCount = pack(entity, snapshots, tarOutput);
            return new PackResult(entryCount + 1);
        }
    }

    private static void packMetadata(OriginWriter writeMetadata, TarArchiveOutputStream tarOutput) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeMetadata.execute(output);
        createTarEntry(METADATA_PATH, output.size(), UnixPermissions.FILE_FLAG | UnixPermissions.DEFAULT_FILE_PERM, tarOutput);
        tarOutput.write(output.toByteArray());
        tarOutput.closeArchiveEntry();
    }

    private long pack(CacheableEntity entity, Map<String, ? extends FileSystemSnapshot> snapshots, TarArchiveOutputStream tarOutput) {
        AtomicLong entries = new AtomicLong();
        entity.visitOutputTrees((treeName, type, root) -> {
            FileSystemSnapshot treeSnapshots = snapshots.get(treeName);
            try {
                long entryCount = packTree(treeName, type, treeSnapshots, tarOutput);
                entries.addAndGet(entryCount);
            } catch (Exception ex) {
                throw new RuntimeException(String.format("Could not pack tree '%s': %s", treeName, ex.getMessage()), ex);
            }
        });
        return entries.get();
    }

    private long packTree(String name, TreeType type, FileSystemSnapshot snapshots, TarArchiveOutputStream tarOutput) {
        PackingVisitor packingVisitor = new PackingVisitor(tarOutput, name, type);
        snapshots.accept(new RelativePathTracker(), packingVisitor);
        return packingVisitor.getPackedEntryCount();
    }

    private static void createTarEntry(String path, long size, int mode, TarArchiveOutputStream tarOutput) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(path, true);
        entry.setSize(size);
        entry.setMode(mode);
        tarOutput.putArchiveEntry(entry);
    }

    @Override
    public UnpackResult unpack(CacheableEntity entity, InputStream input, OriginReader readOrigin) throws IOException {
        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(input, ENCODING.name())) {
            return unpack(entity, tarInput, readOrigin);
        }
    }

    private UnpackResult unpack(CacheableEntity entity, TarArchiveInputStream tarInput, OriginReader readOriginAction) throws IOException {
        ImmutableMap.Builder<String, CacheableTree> treesBuilder = ImmutableMap.builder();
        entity.visitOutputTrees((name, type, root) -> treesBuilder.put(name, new CacheableTree(type, root)));
        ImmutableMap<String, CacheableTree> treesByName = treesBuilder.build();

        TarArchiveEntry tarEntry;
        OriginMetadata originMetadata = null;
        Map<String, FileSystemLocationSnapshot> snapshots = new HashMap<>();

        tarEntry = tarInput.getNextTarEntry();
        AtomicLong entries = new AtomicLong();
        while (tarEntry != null) {
            entries.incrementAndGet();
            String path = safeEntryName(tarEntry);

            if (path.equals(METADATA_PATH)) {
                // handle origin metadata
                originMetadata = readOriginAction.execute(CloseShieldInputStream.wrap(tarInput));
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
                tarEntry = unpackTree(treeName, tree.getType(), tree.getRoot(), tarInput, tarEntry, childPath, missing, snapshots, entries);
            }
        }
        if (originMetadata == null) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }

        return new UnpackResult(originMetadata, entries.get(), snapshots);
    }

    private static class CacheableTree {
        private final TreeType type;
        private final File root;

        public CacheableTree(TreeType type, File root) {
            this.type = type;
            this.root = root;
        }

        public TreeType getType() {
            return type;
        }

        public File getRoot() {
            return root;
        }
    }

    @Nullable
    private TarArchiveEntry unpackTree(String treeName, TreeType treeType, File treeRoot, TarArchiveInputStream input, TarArchiveEntry rootEntry, String childPath, boolean missing, Map<String, FileSystemLocationSnapshot> snapshots, AtomicLong entries) throws IOException {
        boolean isDirEntry = rootEntry.isDirectory();
        boolean root = Strings.isNullOrEmpty(childPath);
        if (!root) {
            throw new IllegalStateException("Root needs to be the first entry in a tree");
        }
        // We are handling the root of the tree here
        if (missing) {
            fileSystemSupport.ensureFileIsMissing(treeRoot);
            return input.getNextTarEntry();
        }

        fileSystemSupport.ensureDirectoryForTree(treeType, treeRoot);
        if (treeType == TreeType.FILE) {
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

    private RegularFileSnapshot unpackFile(TarArchiveInputStream input, TarArchiveEntry entry, File file, String fileName) throws IOException {
        try (CountingOutputStream output = new CountingOutputStream(new FileOutputStream(file))) {
            HashCode hash = streamHasher.hashCopy(input, output);
            chmodUnpackedFile(entry, file);
            String internedAbsolutePath = stringInterner.intern(file.getAbsolutePath());
            String internedFileName = stringInterner.intern(fileName);
            return new RegularFileSnapshot(internedAbsolutePath, internedFileName, hash, DefaultFileMetadata.file(output.getCount(), file.lastModified(), DIRECT));
        }
    }

    @Nullable
    private TarArchiveEntry unpackDirectoryTree(TarArchiveInputStream input, TarArchiveEntry rootEntry, Map<String, FileSystemLocationSnapshot> snapshots, AtomicLong entries, File treeRoot, String treeName) throws IOException {
        RelativePathParser parser = new RelativePathParser(safeEntryName(rootEntry));

        DirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.noSortingRequired();
        builder.enterDirectory(DIRECT, stringInterner.intern(treeRoot.getAbsolutePath()), stringInterner.intern(treeRoot.getName()), INCLUDE_EMPTY_DIRS);

        TarArchiveEntry entry;

        while ((entry = input.getNextTarEntry()) != null) {
            boolean isDir = entry.isDirectory();
            boolean outsideOfRoot = parser.nextPath(safeEntryName(entry), isDir, builder::leaveDirectory);
            if (outsideOfRoot) {
                break;
            }
            entries.incrementAndGet();

            File file = new File(treeRoot, parser.getRelativePath());
            if (isDir) {
                FileUtils.forceMkdir(file);
                chmodUnpackedFile(entry, file);
                String internedAbsolutePath = stringInterner.intern(file.getAbsolutePath());
                String internedName = stringInterner.intern(parser.getName());
                builder.enterDirectory(DIRECT, internedAbsolutePath, internedName, INCLUDE_EMPTY_DIRS);
            } else {
                RegularFileSnapshot fileSnapshot = unpackFile(input, entry, file, parser.getName());
                builder.visitLeafElement(fileSnapshot);
            }
        }

        parser.exitToRoot(builder::leaveDirectory);
        builder.leaveDirectory();

        snapshots.put(treeName, builder.getResult());
        return entry;
    }

    /**
     * Returns a safe name for the name of a tar archive entry.
     *
     * @see PathTraversalChecker#safePathName(String)
     */
    private static String safeEntryName(TarArchiveEntry tarEntry) {
        return PathTraversalChecker.safePathName(tarEntry.getName());
    }

    private void chmodUnpackedFile(TarArchiveEntry entry, File file) {
        filePermissionAccess.chmod(file, entry.getMode() & UnixPermissions.PERM_MASK);
    }

    private static String escape(String name) {
        try {
            return URLEncoder.encode(name, ENCODING.name());
        } catch (UnsupportedEncodingException ignored) {
            throw new AssertionError();
        }
    }

    private static String unescape(String name) {
        try {
            return URLDecoder.decode(name, ENCODING.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private class PackingVisitor implements RelativePathTrackingFileSystemSnapshotHierarchyVisitor {
        private final TarArchiveOutputStream tarOutput;
        private final String treePath;
        private final String treeRoot;
        private final TreeType type;

        private long packedEntryCount;

        public PackingVisitor(TarArchiveOutputStream tarOutput, String treeName, TreeType type) {
            this.tarOutput = tarOutput;
            this.treePath = "tree-" + escape(treeName);
            this.treeRoot = treePath + "/";
            this.type = type;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, RelativePathSupplier relativePath) {
            boolean isRoot = relativePath.isRoot();
            String targetPath = getTargetPath(relativePath);
            snapshot.accept(new FileSystemLocationSnapshotVisitor() {
                @Override
                public void visitDirectory(DirectorySnapshot directorySnapshot) {
                    assertCorrectType(isRoot, snapshot);
                    File dir = new File(snapshot.getAbsolutePath());
                    int dirMode = isRoot ? UnixPermissions.DEFAULT_DIR_PERM : filePermissionAccess.getUnixMode(dir);
                    storeDirectoryEntry(targetPath, dirMode, tarOutput);
                }

                @Override
                public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    assertCorrectType(isRoot, snapshot);
                    File file = new File(snapshot.getAbsolutePath());
                    int fileMode = filePermissionAccess.getUnixMode(file);
                    storeFileEntry(file, targetPath, file.length(), fileMode, tarOutput);
                }

                @Override
                public void visitMissing(MissingFileSnapshot missingSnapshot) {
                    if (!isRoot) {
                        throw new RuntimeException(String.format("Couldn't read content of file '%s'", snapshot.getAbsolutePath()));
                    }
                    storeMissingTree(targetPath, tarOutput);
                }
            });
            packedEntryCount++;
            return SnapshotVisitResult.CONTINUE;
        }

        public long getPackedEntryCount() {
            return packedEntryCount;
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

        private String getTargetPath(RelativePathSupplier relativePath) {
            return relativePath.isRoot()
                ? treePath
                : treeRoot + relativePath.toRelativePath();
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
                try (FileInputStream input = new FileInputStream(inputFile)) {
                    IOUtils.copyLarge(input, tarOutput, bufferProvider.getBuffer());
                }
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
