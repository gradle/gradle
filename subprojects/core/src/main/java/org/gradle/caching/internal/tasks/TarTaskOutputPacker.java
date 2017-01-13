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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.collections.DefaultDirectoryWalkerFactory;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.specs.Specs;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
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
    private static final Pattern PROPERTY_PATH = Pattern.compile("property-([^/]+)(?:/(.*))?");
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final DefaultDirectoryWalkerFactory directoryWalkerFactory;
    private final FileSystem fileSystem;

    public TarTaskOutputPacker(FileSystem fileSystem) {
        this.directoryWalkerFactory = new DefaultDirectoryWalkerFactory(JavaVersion.current(), fileSystem);
        this.fileSystem = fileSystem;
    }

    @Override
    public void pack(final TaskOutputsInternal taskOutputs, OutputStream output, final TaskOutputOriginWriter writeOrigin) {
        IoActions.withResource(new TarOutputStream(output, "utf-8"), new Action<TarOutputStream>() {
            @Override
            public void execute(TarOutputStream outputStream) {
                outputStream.setLongFileMode(TarOutputStream.LONGFILE_POSIX);
                outputStream.setBigNumberMode(TarOutputStream.BIGNUMBER_POSIX);
                outputStream.setAddPaxHeadersForNonAsciiNames(true);
                try {
                    packMetadata(writeOrigin, outputStream);
                    pack(taskOutputs, outputStream);
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
        try {
            outputStream.write(baos.toByteArray());
        } finally {
            outputStream.closeEntry();
        }
    }

    private void pack(TaskOutputsInternal taskOutputs, TarOutputStream outputStream) {
        for (TaskOutputFilePropertySpec spec : taskOutputs.getFileProperties()) {
            try {
                packProperty((CacheableTaskOutputFilePropertySpec) spec, outputStream);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack property '%s': %s", spec.getPropertyName(), ex.getMessage()), ex);
            }
        }
    }

    private void packProperty(CacheableTaskOutputFilePropertySpec propertySpec, final TarOutputStream outputStream) throws IOException {
        final String propertyName = propertySpec.getPropertyName();
        File outputFile = propertySpec.getOutputFile();
        if (outputFile == null) {
            return;
        }
        switch (propertySpec.getOutputType()) {
            case DIRECTORY:
                storeDirectoryProperty(propertyName, outputFile, outputStream);
                break;
            case FILE:
                storeFileProperty(propertyName, outputFile, outputStream);
                break;
            default:
                throw new AssertionError();
        }
    }

    private void storeDirectoryProperty(String propertyName, File directory, final TarOutputStream outputStream) throws IOException {
        if (!directory.exists()) {
            return;
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a directory", directory));
        }
        final String propertyRoot = "property-" + propertyName + "/";
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

    private void storeFileProperty(String propertyName, File file, TarOutputStream outputStream) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a file", file));
        }
        String path = "property-" + propertyName;
        storeFileEntry(file, path, file.lastModified(), file.length(), fileSystem.getUnixMode(file), outputStream);
    }

    private void storeDirectoryEntry(FileVisitDetails dirDetails, String propertyRoot, TarOutputStream outputStream) throws IOException {
        String path = dirDetails.getRelativePath().getPathString();
        TarEntry entry = new TarEntry(propertyRoot + path + "/");
        storeModificationTime(entry, dirDetails.getLastModified());
        entry.setMode(UnixStat.DIR_FLAG | dirDetails.getMode());
        outputStream.putNextEntry(entry);
        outputStream.closeEntry();
    }

    private void storeFileEntry(File file, String path, long lastModified, long size, int mode, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry(path);
        storeModificationTime(entry, lastModified);
        entry.setSize(size);
        entry.setMode(UnixStat.FILE_FLAG | mode);
        outputStream.putNextEntry(entry);
        try {
            Files.copy(file, outputStream);
        } finally {
            outputStream.closeEntry();
        }
    }

    @Override
    public void unpack(final TaskOutputsInternal taskOutputs, InputStream input, final TaskOutputOriginReader readOrigin) {
        IoActions.withResource(new TarInputStream(input), new Action<TarInputStream>() {
            @Override
            public void execute(TarInputStream tarInput) {
                try {
                    unpack(taskOutputs, tarInput, readOrigin);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private void unpack(TaskOutputsInternal taskOutputs, TarInputStream tarInput, TaskOutputOriginReader readOriginAction) throws IOException {
        Map<String, TaskOutputFilePropertySpec> propertySpecs = Maps.uniqueIndex(taskOutputs.getFileProperties(), new Function<TaskFilePropertySpec, String>() {
            @Override
            public String apply(TaskFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
        boolean originSeen = false;
        TarEntry entry;
        while ((entry = tarInput.getNextEntry()) != null) {
            String name = entry.getName();

            if (name.equals(METADATA_PATH)) {
                // handle origin metadata
                originSeen = true;
                readOriginAction.execute(new CloseShieldInputStream(tarInput));
            } else {
                // handle output property
                Matcher matcher = PROPERTY_PATH.matcher(name);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Cached result format error, invalid contents: " + name);
                }
                String propertyName = matcher.group(1);
                CacheableTaskOutputFilePropertySpec propertySpec = (CacheableTaskOutputFilePropertySpec) propertySpecs.get(propertyName);
                if (propertySpec == null) {
                    throw new IllegalStateException(String.format("No output property '%s' registered", propertyName));
                }

                File specRoot = propertySpec.getOutputFile();
                String path = matcher.group(2);
                File outputFile;
                if (Strings.isNullOrEmpty(path)) {
                    outputFile = specRoot;
                } else {
                    outputFile = new File(specRoot, path);
                }
                if (entry.isDirectory()) {
                    if (propertySpec.getOutputType() != OutputType.DIRECTORY) {
                        throw new IllegalStateException("Property should be an output directory property: " + propertyName);
                    }
                    FileUtils.forceMkdir(outputFile);
                } else {
                    Files.asByteSink(outputFile).writeFrom(tarInput);
                }
                //noinspection OctalInteger
                fileSystem.chmod(outputFile, entry.getMode() & 0777);
                long lastModified = getModificationTime(entry);
                if (!outputFile.setLastModified(lastModified)) {
                    throw new UnsupportedOperationException(String.format("Could not set modification time for '%s'", outputFile));
                }
            }
        }
        if (!originSeen) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }
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
