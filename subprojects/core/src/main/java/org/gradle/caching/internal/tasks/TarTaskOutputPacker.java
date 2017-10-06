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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Maps;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tools.zip.UnixStat;
import org.gradle.api.GradleException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DirectoryFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.RegularFileSnapshot;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.OutputType;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.ensureDirectoryForProperty;
import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.makeDirectory;

/**
 * Packages task output to a POSIX TAR file.
 */
@SuppressWarnings("Since15")
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
    public PackResult pack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, Map<String, Map<String, FileContentSnapshot>> outputSnapshots, OutputStream output, TaskOutputOriginWriter writeOrigin) throws IOException {
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
            long entryCount = pack(propertySpecs, outputSnapshots, tarOutput);
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

    private long pack(Collection<ResolvedTaskOutputFilePropertySpec> propertySpecs, Map<String, Map<String, FileContentSnapshot>> outputSnapshots, TarArchiveOutputStream tarOutput) {
        long entries = 0;
        for (ResolvedTaskOutputFilePropertySpec propertySpec : propertySpecs) {
            String propertyName = propertySpec.getPropertyName();
            Map<String, FileContentSnapshot> outputs = outputSnapshots.get(propertyName);
            try {
                entries += packProperty(propertySpec, outputs, tarOutput);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack property '%s': %s", propertyName, ex.getMessage()), ex);
            }
        }
        return entries;
    }

    private long packProperty(CacheableTaskOutputFilePropertySpec propertySpec, Map<String, FileContentSnapshot> outputSnapshots, TarArchiveOutputStream tarOutput) throws IOException {
        String propertyName = propertySpec.getPropertyName();
        File root = propertySpec.getOutputFile();
        if (root == null) {
            return 0;
        }
        String propertyPath = "property-" + propertyName;
        if (outputSnapshots.isEmpty()) {
            storeMissingProperty(propertyPath, tarOutput);
            return 1;
        }
        switch (propertySpec.getOutputType()) {
            case DIRECTORY:
                return storeDirectoryProperty(propertyPath, root, outputSnapshots, tarOutput);
            case FILE:
                storeFileProperty(propertyPath, root, tarOutput);
                return 1;
            default:
                throw new AssertionError();
        }
    }

    private long storeDirectoryProperty(String propertyPath, File directory, Map<String, FileContentSnapshot> outputSnapshots, final TarArchiveOutputStream tarOutput) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a directory", directory));
        }

        long entries = 0;

        final String propertyRoot = propertyPath + "/";
        createTarEntry(propertyRoot, 0, UnixStat.DIR_FLAG | UnixStat.DEFAULT_DIR_PERM, tarOutput);
        tarOutput.closeArchiveEntry();
        entries++;

        String rootAbsolutePath = directory.getAbsolutePath();
        Path rootPath = directory.toPath();

        for (Map.Entry<String, FileContentSnapshot> entry : outputSnapshots.entrySet()) {
            String absolutePath = entry.getKey();
            // We've already created the directory for the property
            if (absolutePath.equals(rootAbsolutePath)) {
                continue;
            }
            File file = new File(absolutePath);
            String relativePath = rootPath.relativize(file.toPath()).toString();
            String targetPath = propertyRoot + relativePath;
            int mode = fileSystem.getUnixMode(file);
            switch (entry.getValue().getType()) {
                case RegularFile:
                    storeFileEntry(file, targetPath, file.length(), mode, tarOutput);
                    break;
                case Directory:
                    storeDirectoryEntry(targetPath, mode, tarOutput);
                    break;
                case Missing:
                    throw new IllegalStateException("File should not be missing: " + file);
                default:
                    throw new AssertionError();
            }
            entries++;
        }
        return entries;
    }

    private void storeFileProperty(String propertyPath, File file, TarArchiveOutputStream tarOutput) throws IOException {
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a file", file));
        }
        storeFileEntry(file, propertyPath, file.length(), fileSystem.getUnixMode(file), tarOutput);
    }

    private void storeMissingProperty(String propertyPath, TarArchiveOutputStream tarOutput) throws IOException {
        createTarEntry("missing-" + propertyPath, 0, UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM, tarOutput);
        tarOutput.closeArchiveEntry();
    }

    private void storeDirectoryEntry(String path, int mode, TarArchiveOutputStream tarOutput) throws IOException {
        createTarEntry(path + "/", 0, UnixStat.DIR_FLAG | mode, tarOutput);
        tarOutput.closeArchiveEntry();
    }

    private void storeFileEntry(File inputFile, String path, long size, int mode, TarArchiveOutputStream tarOutput) throws IOException {
        createTarEntry(path, size, UnixStat.FILE_FLAG | mode, tarOutput);
        FileInputStream input = new FileInputStream(inputFile);
        try {
            IOUtils.copyLarge(input, tarOutput, COPY_BUFFERS.get());
        } finally {
            IOUtils.closeQuietly(input);
        }
        tarOutput.closeArchiveEntry();
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
        TaskOutputOriginMetadata originMetadata = null;
        ImmutableListMultimap.Builder<String, FileSnapshot> propertyFileSnapshots = ImmutableListMultimap.builder();

        long entries = 0;
        while ((tarEntry = tarInput.getNextTarEntry()) != null) {
            ++entries;
            String name = tarEntry.getName();

            if (name.equals(METADATA_PATH)) {
                // handle origin metadata
                originMetadata = readOriginAction.execute(new CloseShieldInputStream(tarInput));
            } else {
                // handle output property
                Matcher matcher = PROPERTY_PATH.matcher(name);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Cached result format error, invalid contents: " + name);
                }

                String propertyName = matcher.group(2);
                ResolvedTaskOutputFilePropertySpec propertySpec = propertySpecsMap.get(propertyName);
                if (propertySpec == null) {
                    throw new IllegalStateException(String.format("No output property '%s' registered", propertyName));
                }

                boolean outputMissing = matcher.group(1) != null;
                String childPath = matcher.group(3);
                unpackPropertyEntry(propertySpec, tarInput, tarEntry, childPath, outputMissing, propertyFileSnapshots);
            }
        }
        if (originMetadata == null) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }

        return new UnpackResult(originMetadata, entries, propertyFileSnapshots.build());
    }

    private void unpackPropertyEntry(ResolvedTaskOutputFilePropertySpec propertySpec, InputStream input, TarArchiveEntry entry, String childPath, boolean missing, ImmutableMultimap.Builder<String, FileSnapshot> fileSnapshots) throws IOException {
        File propertyRoot = propertySpec.getOutputFile();
        String propertyName = propertySpec.getPropertyName();
        if (propertyRoot == null) {
            throw new IllegalStateException("Optional property should have a value: " + propertyName);
        }

        File outputFile;
        boolean isDirEntry = entry.isDirectory();
        boolean root = Strings.isNullOrEmpty(childPath);
        if (root) {
            // We are handling the root of the property here
            if (missing) {
                if (!makeDirectory(propertyRoot.getParentFile())) {
                    // Make sure output is removed if it exists already
                    if (propertyRoot.exists()) {
                        FileUtils.forceDelete(propertyRoot);
                    }
                }
                return;
            }

            OutputType outputType = propertySpec.getOutputType();
            if (isDirEntry) {
                if (outputType != OutputType.DIRECTORY) {
                    throw new IllegalStateException("Property should be an output directory property: " + propertyName);
                }
            } else {
                if (outputType == OutputType.DIRECTORY) {
                    throw new IllegalStateException("Property should be an output file property: " + propertyName);
                }
            }
            ensureDirectoryForProperty(outputType, propertyRoot);
            outputFile = propertyRoot;
        } else {
            outputFile = new File(propertyRoot, childPath);
        }

        String internedPath = stringInterner.intern(outputFile.getAbsolutePath());
        RelativePath relativePath = root ? RelativePath.parse(!isDirEntry, outputFile.getName()) : RelativePath.parse(!isDirEntry, childPath);
        if (isDirEntry) {
            FileUtils.forceMkdir(outputFile);
            fileSnapshots.put(propertyName, new DirectoryFileSnapshot(internedPath, relativePath, root));
        } else {
            OutputStream output = new FileOutputStream(outputFile);
            HashCode hash;
            try {
                hash = streamHasher.hashCopy(input, output);
            } finally {
                IOUtils.closeQuietly(output);
            }
            FileHashSnapshot contentSnapshot = new FileHashSnapshot(hash, outputFile.lastModified());
            fileSnapshots.put(propertyName, new RegularFileSnapshot(internedPath, relativePath, root, contentSnapshot));
        }

        fileSystem.chmod(outputFile, entry.getMode() & FILE_PERMISSION_MASK);
    }
}
