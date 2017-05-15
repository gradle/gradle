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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;
import org.apache.tools.zip.UnixStat;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.DefaultDirectoryWalkerFactory;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.specs.Specs;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Packages task output to a POSIX TAR file. Because Ant's TAR implementation
 * supports only 1 second precision for file modification times, we encode the
 * fractional nanoseconds into the group ID of the file.
 */
public class TarTaskOutputPacker implements TaskOutputPacker {
    private static final String METADATA_PATH = "METADATA";
    private static final Pattern PROPERTY_PATH = Pattern.compile("(missing-)?property-([^/]+)(?:/(.*))?");
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final DefaultDirectoryWalkerFactory directoryWalkerFactory;
    private final FileSystem fileSystem;

    public TarTaskOutputPacker(FileSystem fileSystem) {
        this.directoryWalkerFactory = new DefaultDirectoryWalkerFactory(JavaVersion.current(), fileSystem);
        this.fileSystem = fileSystem;
    }

    @Override
    public void pack(final SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, OutputStream output, final TaskOutputOriginWriter writeOrigin) {
        IoActions.withResource(new TarOutputStream(output, "utf-8"), new Action<TarOutputStream>() {
            @Override
            public void execute(TarOutputStream outputStream) {
                outputStream.setLongFileMode(TarOutputStream.LONGFILE_POSIX);
                outputStream.setBigNumberMode(TarOutputStream.BIGNUMBER_POSIX);
                outputStream.setAddPaxHeadersForNonAsciiNames(true);
                try {
                    packMetadata(writeOrigin, outputStream);
                    pack(propertySpecs, outputStream);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private void packMetadata(TaskOutputOriginWriter writeMetadata, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry(METADATA_PATH);
        entry.setMode(UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeMetadata.execute(baos);
        entry.setSize(baos.size());
        outputStream.putNextEntry(entry);
        outputStream.write(baos.toByteArray());
        outputStream.closeEntry();
    }

    private void pack(Collection<ResolvedTaskOutputFilePropertySpec> propertySpecs, TarOutputStream outputStream) {
        for (ResolvedTaskOutputFilePropertySpec spec : propertySpecs) {
            try {
                packProperty(spec, outputStream);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack property '%s': %s", spec.getPropertyName(), ex.getMessage()), ex);
            }
        }
    }

    private void packProperty(CacheableTaskOutputFilePropertySpec propertySpec, TarOutputStream outputStream) throws IOException {
        String propertyName = propertySpec.getPropertyName();
        File outputFile = propertySpec.getOutputFile();
        if (outputFile == null) {
            return;
        }
        String propertyPath = "property-" + propertyName;
        if (!outputFile.exists()) {
            storeMissingProperty(propertyPath, outputStream);
            return;
        }
        switch (propertySpec.getOutputType()) {
            case DIRECTORY:
                storeDirectoryProperty(propertyPath, outputFile, outputStream);
                break;
            case FILE:
                storeFileProperty(propertyPath, outputFile, outputStream);
                break;
            default:
                throw new AssertionError();
        }
    }

    private void storeDirectoryProperty(String propertyPath, File directory, final TarOutputStream outputStream) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a directory", directory));
        }
        final String propertyRoot = propertyPath + "/";
        outputStream.putNextEntry(new TarEntry(propertyRoot));
        outputStream.closeEntry();
        FileVisitor visitor = new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                try {
                    storeDirectoryEntry(dirDetails, propertyRoot, outputStream);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                try {
                    String path = propertyRoot + fileDetails.getRelativePath().getPathString();
                    storeFileEntry(fileDetails.getFile(), path, fileDetails.getLastModified(), fileDetails.getSize(), fileDetails.getMode(), outputStream);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        directoryWalkerFactory.create().walkDir(directory, RelativePath.EMPTY_ROOT, visitor, Specs.satisfyAll(), new AtomicBoolean(), false);
    }

    private void storeFileProperty(String propertyPath, File file, TarOutputStream outputStream) throws IOException {
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a file", file));
        }
        storeFileEntry(file, propertyPath, file.lastModified(), file.length(), fileSystem.getUnixMode(file), outputStream);
    }

    private void storeMissingProperty(String propertyPath, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry("missing-" + propertyPath);
        outputStream.putNextEntry(entry);
        outputStream.closeEntry();
    }

    private void storeDirectoryEntry(FileVisitDetails dirDetails, String propertyRoot, TarOutputStream outputStream) throws IOException {
        String path = dirDetails.getRelativePath().getPathString();
        createTarEntry(propertyRoot + path + "/", dirDetails.getLastModified(), 0, UnixStat.DIR_FLAG | dirDetails.getMode(), outputStream);
        outputStream.closeEntry();
    }

    private void storeFileEntry(File file, String path, long lastModified, long size, int mode, TarOutputStream outputStream) throws IOException {
        createTarEntry(path, lastModified, size, UnixStat.FILE_FLAG | mode, outputStream);
        Files.copy(file, outputStream);
        outputStream.closeEntry();
    }

    private static void createTarEntry(String path, long lastModified, long size, int mode, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry(path);
        storeModificationTime(entry, lastModified);
        entry.setSize(size);
        entry.setMode(mode);
        outputStream.putNextEntry(entry);
    }

    @Override
    public TaskOutputOriginMetadata unpack(final SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, InputStream input, final TaskOutputOriginReader readOrigin) {
        return IoActions.withResource(new TarInputStream(input), new Transformer<TaskOutputOriginMetadata, TarInputStream>() {
            @Override
            public TaskOutputOriginMetadata transform(TarInputStream tarInput) {
                try {
                    return unpack(propertySpecs, tarInput, readOrigin);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private TaskOutputOriginMetadata unpack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, TarInputStream tarInput, TaskOutputOriginReader readOriginAction) throws IOException {
        Map<String, ResolvedTaskOutputFilePropertySpec> propertySpecsMap = Maps.uniqueIndex(propertySpecs, new Function<TaskFilePropertySpec, String>() {
            @Override
            public String apply(TaskFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
        TarEntry entry;
        TaskOutputOriginMetadata originMetadata = null;
        while ((entry = tarInput.getNextEntry()) != null) {
            String name = entry.getName();

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
                unpackPropertyEntry(propertySpec, tarInput, entry, childPath, outputMissing);
            }
        }
        if (originMetadata == null) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }

        return originMetadata;
    }

    private void unpackPropertyEntry(ResolvedTaskOutputFilePropertySpec propertySpec, InputStream input, TarEntry entry, String childPath, boolean missing) throws IOException {
        File propertyRoot = propertySpec.getOutputFile();
        if (propertyRoot == null) {
            throw new IllegalStateException("Optional property should have a value: " + propertySpec.getPropertyName());
        }

        File outputFile;
        boolean isDirEntry = entry.isDirectory();
        if (Strings.isNullOrEmpty(childPath)) {
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
                    throw new IllegalStateException("Property should be an output directory property: " + propertySpec.getPropertyName());
                }
            } else {
                if (outputType == OutputType.DIRECTORY) {
                    throw new IllegalStateException("Property should be an output file property: " + propertySpec.getPropertyName());
                }
            }
            ensureDirectoryForProperty(outputType, propertyRoot);
            outputFile = propertyRoot;
        } else {
            outputFile = new File(propertyRoot, childPath);
        }

        if (isDirEntry) {
            FileUtils.forceMkdir(outputFile);
        } else {
            Files.asByteSink(outputFile).writeFrom(input);
        }

        //noinspection OctalInteger
        fileSystem.chmod(outputFile, entry.getMode() & 0777);
        long lastModified = getModificationTime(entry);
        if (!outputFile.setLastModified(lastModified)) {
            throw new UnsupportedOperationException(String.format("Could not set modification time for '%s'", outputFile));
        }
    }

    @VisibleForTesting
    static void ensureDirectoryForProperty(OutputType outputType, File specRoot) throws IOException {
        switch (outputType) {
            case DIRECTORY:
                if (!makeDirectory(specRoot)) {
                    FileUtils.cleanDirectory(specRoot);
                }
                break;
            case FILE:
                if (!makeDirectory(specRoot.getParentFile())) {
                    if (specRoot.exists()) {
                        FileUtils.forceDelete(specRoot);
                    }
                }
                break;
            default:
                throw new AssertionError();
        }
    }

    private static boolean makeDirectory(File output) throws IOException {
        if (output.isDirectory()) {
            return false;
        } else if (output.isFile()) {
            FileUtils.forceDelete(output);
        }
        FileUtils.forceMkdir(output);
        return true;
    }

    private static void storeModificationTime(TarEntry entry, long lastModified) {
        // This will be divided by 1000 internally
        entry.setModTime(lastModified);
        // Store excess nanoseconds in group ID
        long excessNanos = TimeUnit.MILLISECONDS.toNanos(lastModified % 1000);
        // Store excess nanos as negative number to distinguish real group IDs
        entry.setGroupId(-excessNanos);
    }

    private static long getModificationTime(TarEntry entry) {
        long lastModified = entry.getModTime().getTime();
        long excessNanos = -entry.getLongGroupId();
        if (excessNanos < 0 || excessNanos >= NANOS_PER_SECOND) {
            throw new IllegalStateException("Invalid excess nanos: " + excessNanos);
        }
        lastModified += TimeUnit.NANOSECONDS.toMillis(excessNanos);
        return lastModified;
    }
}
