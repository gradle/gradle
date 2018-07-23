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

package org.gradle.caching.internal.tasks;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tools.zip.UnixStat;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.mirror.MerkleDirectorySnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathStringTracker;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.api.internal.tasks.OutputType;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

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

import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.ensureDirectoryForProperty;
import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.makeDirectory;

/**
 * Packages task output to a POSIX TAR file.
 */
@NonNullApi
public class TarTaskOutputPacker implements TaskOutputPacker {
    private static final String METADATA_PATH = "METADATA";
    private static final Pattern PROPERTY_PATH = Pattern.compile("(missing-)?property-([^/]+)(?:/(.*))?");
    @SuppressWarnings("OctalInteger")
    private static final int FILE_PERMISSION_MASK = 0777;
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

    public TarTaskOutputPacker(FileSystem fileSystem, StreamHasher streamHasher, StringInterner stringInterner) {
        this.fileSystem = fileSystem;
        this.streamHasher = streamHasher;
        this.stringInterner = stringInterner;
    }

    @Override
    public PackResult pack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, Map<String, CurrentFileCollectionFingerprint> outputFingerprints, OutputStream output, TaskOutputOriginWriter writeOrigin) throws IOException {
        BufferedOutputStream bufferedOutput;
        if (output instanceof BufferedOutputStream) {
            bufferedOutput = (BufferedOutputStream) output;
        } else {
            bufferedOutput = new BufferedOutputStream(output);
        }
        TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(bufferedOutput, "utf-8");
        try {
            tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOutput.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tarOutput.setAddPaxHeadersForNonAsciiNames(true);
            packMetadata(writeOrigin, tarOutput);
            long entryCount = pack(propertySpecs, outputFingerprints, tarOutput);
            return new PackResult(entryCount + 1);
        } finally {
            IOUtils.closeQuietly(tarOutput);
        }
    }

    private void packMetadata(TaskOutputOriginWriter writeMetadata, TarArchiveOutputStream tarOutput) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeMetadata.execute(baos);
        createTarEntry(METADATA_PATH, baos.size(), UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM, tarOutput);
        tarOutput.write(baos.toByteArray());
        tarOutput.closeArchiveEntry();
    }

    private long pack(Collection<ResolvedTaskOutputFilePropertySpec> propertySpecs, Map<String, CurrentFileCollectionFingerprint> outputFingerprints, TarArchiveOutputStream tarOutput) {
        long entries = 0;
        for (ResolvedTaskOutputFilePropertySpec propertySpec : propertySpecs) {
            String propertyName = propertySpec.getPropertyName();
            CurrentFileCollectionFingerprint outputFingerprint = outputFingerprints.get(propertyName);
            try {
                entries += packProperty(propertySpec, outputFingerprint, tarOutput);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack property '%s': %s", propertyName, ex.getMessage()), ex);
            }
        }
        return entries;
    }

    private long packProperty(final CacheableTaskOutputFilePropertySpec propertySpec, CurrentFileCollectionFingerprint outputFingerprint, TarArchiveOutputStream tarOutput) {
        String propertyName = propertySpec.getPropertyName();
        File root = propertySpec.getOutputFile();
        if (root == null) {
            return 0;
        }
        PackingVisitor packingVisitor = new PackingVisitor(tarOutput, propertyName, propertySpec.getOutputType(), fileSystem);
        outputFingerprint.visitRoots(packingVisitor);
        return packingVisitor.finish();
    }

    private static void createTarEntry(String path, long size, int mode, TarArchiveOutputStream tarOutput) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(path, true);
        entry.setSize(size);
        entry.setMode(mode);
        tarOutput.putArchiveEntry(entry);
    }

    @Override
    public UnpackResult unpack(final SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, final InputStream input, final TaskOutputOriginReader readOrigin) throws IOException {
        TarArchiveInputStream tarInput = new TarArchiveInputStream(input);
        try {
            return unpack(propertySpecs, tarInput, readOrigin);
        } finally {
            IOUtils.closeQuietly(tarInput);
        }
    }

    private UnpackResult unpack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, TarArchiveInputStream tarInput, TaskOutputOriginReader readOriginAction) throws IOException {
        Map<String, ResolvedTaskOutputFilePropertySpec> propertySpecsMap = Maps.uniqueIndex(propertySpecs, new Function<TaskFilePropertySpec, String>() {
            @Override
            public String apply(TaskFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
        TarArchiveEntry tarEntry;
        OriginTaskExecutionMetadata originMetadata = null;
        Map<String, PhysicalSnapshot> fileSnapshots = new HashMap<String, PhysicalSnapshot>();

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
                // handle output property
                Matcher matcher = PROPERTY_PATH.matcher(path);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Cached result format error, invalid contents: " + path);
                }

                String propertyName = unescape(matcher.group(2));
                ResolvedTaskOutputFilePropertySpec propertySpec = propertySpecsMap.get(propertyName);
                if (propertySpec == null) {
                    throw new IllegalStateException(String.format("No output property '%s' registered", propertyName));
                }

                boolean outputMissing = matcher.group(1) != null;
                String childPath = matcher.group(3);
                tarEntry = unpackPropertyEntry(propertySpec, tarInput, tarEntry, childPath, outputMissing, fileSnapshots, entries);
            }
        }
        if (originMetadata == null) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }

        return new UnpackResult(originMetadata, entries.get(), fileSnapshots);
    }

    @Nullable
    private TarArchiveEntry unpackPropertyEntry(ResolvedTaskOutputFilePropertySpec propertySpec, TarArchiveInputStream input, TarArchiveEntry rootEntry, String childPath, boolean missing, Map<String, PhysicalSnapshot> snapshots, AtomicInteger entries) throws IOException {
        File propertyRoot = propertySpec.getOutputFile();
        String propertyName = propertySpec.getPropertyName();
        if (propertyRoot == null) {
            throw new IllegalStateException("Optional property should have a value: " + propertyName);
        }

        boolean isDirEntry = rootEntry.isDirectory();
        boolean root = Strings.isNullOrEmpty(childPath);
        if (!root) {
            throw new IllegalStateException("Root needs to be the first entry in a property");
        }
        // We are handling the root of the property here
        if (missing) {
            unpackMissingFile(propertyRoot);
            return input.getNextTarEntry();
        }

        OutputType outputType = propertySpec.getOutputType();

        ensureDirectoryForProperty(outputType, propertyRoot);
        if (outputType == OutputType.FILE) {
            if (isDirEntry) {
                throw new IllegalStateException("Property should be an output file property: " + propertyName);
            }
            PhysicalFileSnapshot fileSnapshot = unpackFile(input, rootEntry, propertyRoot, propertyRoot.getAbsolutePath(), propertyRoot.getName());
            snapshots.put(propertyName, fileSnapshot);
            return input.getNextTarEntry();
        }

        if (!isDirEntry) {
            throw new IllegalStateException("Property should be an output directory property: " + propertyName);
        }
        chmodUnpackedFile(rootEntry, propertyRoot);

        return unpackDirectoryTree(input, rootEntry, snapshots, entries, propertyRoot, propertyName);
    }

    private void unpackMissingFile(File propertyRoot) throws IOException {
        if (!makeDirectory(propertyRoot.getParentFile())) {
            // Make sure output is removed if it exists already
            if (propertyRoot.exists()) {
                FileUtils.forceDelete(propertyRoot);
            }
        }
    }

    private PhysicalFileSnapshot unpackFile(TarArchiveInputStream input, TarArchiveEntry entry, File outputFile, String absolutePath, String fileName) throws IOException {
        OutputStream output = new FileOutputStream(outputFile);
        HashCode hash;
        try {
            hash = streamHasher.hashCopy(input, output);
            chmodUnpackedFile(entry, outputFile);
        } finally {
            IOUtils.closeQuietly(output);
        }
        String outputPath = stringInterner.intern(absolutePath);
        String outputFileName = stringInterner.intern(fileName);
        return new PhysicalFileSnapshot(outputPath, outputFileName, hash, outputFile.lastModified());
    }

    @Nullable
    private TarArchiveEntry unpackDirectoryTree(TarArchiveInputStream input, TarArchiveEntry rootEntry, Map<String, PhysicalSnapshot> snapshots, AtomicInteger entries, File propertyRoot, String propertyName) throws IOException {
        RelativePathParser parser = new RelativePathParser();
        parser.rootPath(rootEntry.getName());

        MerkleDirectorySnapshotBuilder builder = new MerkleDirectorySnapshotBuilder();
        builder.preVisitDirectory(stringInterner.intern(propertyRoot.getAbsolutePath()), stringInterner.intern(propertyRoot.getName()));

        TarArchiveEntry entry;

        while ((entry = input.getNextTarEntry()) != null) {
            entries.incrementAndGet();
            boolean isDir = entry.isDirectory();
            int directoriesLeft = parser.nextPath(entry.getName(), isDir);
            for (int i = 0; i < directoriesLeft; i++) {
                builder.postVisitDirectory(false);
            }
            if (parser.getDepth() == 0) {
                break;
            }

            File outputFile = new File(propertyRoot, parser.getRelativePath());
            if (isDir) {
                FileUtils.forceMkdir(outputFile);
                chmodUnpackedFile(entry, outputFile);
                builder.preVisitDirectory(stringInterner.intern(outputFile.getAbsolutePath()), stringInterner.intern(parser.getName()));
            } else {
                PhysicalFileSnapshot fileSnapshot = unpackFile(input, entry, outputFile, outputFile.getAbsolutePath(), parser.getName());
                builder.visit(fileSnapshot);
            }
        }

        for (int i = 0; i < parser.getDepth(); i++) {
            builder.postVisitDirectory(false);
        }

        snapshots.put(propertyName, builder.getResult());
        return entry;
    }

    private void chmodUnpackedFile(TarArchiveEntry entry, File outputFile) {
        fileSystem.chmod(outputFile, entry.getMode() & FILE_PERMISSION_MASK);
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

    private static class PackingVisitor implements PhysicalSnapshotVisitor {
        private final RelativePathStringTracker relativePathStringTracker;
        private final TarArchiveOutputStream tarOutput;
        private final String propertyPath;
        private final String propertyRoot;
        private final FileSystem fileSystem;
        private final OutputType outputType;

        private long entries;

        public PackingVisitor(TarArchiveOutputStream tarOutput, String propertyName, OutputType outputType, FileSystem fileSystem) {
            this.tarOutput = tarOutput;
            this.propertyPath = "property-" + escape(propertyName);
            this.propertyRoot = propertyPath + "/";
            this.outputType = outputType;
            this.fileSystem = fileSystem;
            this.relativePathStringTracker = new RelativePathStringTracker();
        }

        @Override
        public boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
            boolean root = relativePathStringTracker.isRoot();
            relativePathStringTracker.enter(directorySnapshot);
            assertCorrectType(root, directorySnapshot);
            String targetPath = getTargetPath(root);
            int mode = root ? UnixStat.DEFAULT_DIR_PERM : fileSystem.getUnixMode(new File(directorySnapshot.getAbsolutePath()));
            storeDirectoryEntry(targetPath, mode, tarOutput);
            entries++;
            return true;
        }

        @Override
        public void visit(PhysicalSnapshot fileSnapshot) {
            boolean root = relativePathStringTracker.isRoot();
            relativePathStringTracker.enter(fileSnapshot);
            String targetPath = getTargetPath(root);
            if (fileSnapshot.getType() == FileType.Missing) {
                storeMissingProperty(targetPath, tarOutput);
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
        public void postVisitDirectory() {
            relativePathStringTracker.leave();
        }

        public long finish() {
            if (entries == 0) {
                storeMissingProperty(propertyPath, tarOutput);
                entries++;
            }
            return entries;
        }

        private void assertCorrectType(boolean root, PhysicalSnapshot snapshot) {
            if (root) {
                switch (outputType) {
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
                return propertyPath;
            }
            String relativePath = relativePathStringTracker.getRelativePathString();
            return propertyRoot + relativePath;
        }

        private void storeMissingProperty(String propertyPath, TarArchiveOutputStream tarOutput) {
            try {
                createTarEntry("missing-" + propertyPath, 0, UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM, tarOutput);
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        private void storeDirectoryEntry(String path, int mode, TarArchiveOutputStream tarOutput) {
            try {
                createTarEntry(path + "/", 0, UnixStat.DIR_FLAG | mode, tarOutput);
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void storeFileEntry(File inputFile, String path, long size, int mode, TarArchiveOutputStream tarOutput) {
            try {
                createTarEntry(path, size, UnixStat.FILE_FLAG | mode, tarOutput);
                FileInputStream input = new FileInputStream(inputFile);
                try {
                    IOUtils.copyLarge(input, tarOutput, COPY_BUFFERS.get());
                } finally {
                    IOUtils.closeQuietly(input);
                }
                tarOutput.closeArchiveEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
