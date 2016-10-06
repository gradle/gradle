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

package org.gradle.api.internal.tasks.cache;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;
import org.apache.tools.zip.UnixStat;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
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
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Packages task output to a POSIX TAR file.
 */
public class TarTaskOutputPacker implements TaskOutputPacker {
    private static final Pattern PROPERTY_PATH = Pattern.compile("property-([^/]+)(?:/(.*))?");

    private final DefaultDirectoryWalkerFactory directoryWalkerFactory;
    private final FileSystem fileSystem;

    public TarTaskOutputPacker(FileSystem fileSystem) {
        this.directoryWalkerFactory = new DefaultDirectoryWalkerFactory(JavaVersion.current(), fileSystem);
        this.fileSystem = fileSystem;
    }

    @Override
    public void pack(TaskOutputsInternal taskOutputs, OutputStream output) throws IOException {
        TarOutputStream outputStream = new TarOutputStream(output, "utf-8");
        outputStream.setLongFileMode(TarOutputStream.LONGFILE_POSIX);
        outputStream.setBigNumberMode(TarOutputStream.BIGNUMBER_POSIX);
        outputStream.setAddPaxHeadersForNonAsciiNames(true);
        try {
            pack(taskOutputs, outputStream);
        } finally {
            outputStream.close();
        }
    }

    private void pack(TaskOutputsInternal taskOutputs, TarOutputStream outputStream) {
        for (TaskOutputFilePropertySpec spec : taskOutputs.getFileProperties()) {
            try {
                packProperty((CacheableTaskOutputFilePropertySpec) spec, outputStream);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack property '%s'", spec.getPropertyName()), ex);
            }
        }
    }

    private void packProperty(CacheableTaskOutputFilePropertySpec propertySpec, final TarOutputStream outputStream) throws IOException {
        final String propertyName = propertySpec.getPropertyName();
        File outputFile = propertySpec.getOutputFile();
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
        final String propertyRoot = "property-" + propertyName + "/";
        outputStream.putNextEntry(new TarEntry(propertyRoot));
        FileVisitor visitor = new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                storeDirectoryEntry(dirDetails, propertyRoot, outputStream);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                String path = propertyRoot + fileDetails.getRelativePath().getPathString();
                storeFileEntry(fileDetails.getFile(), path, fileDetails.getLastModified(), fileDetails.getSize(), fileDetails.getMode(), outputStream);
            }
        };
        directoryWalkerFactory.create().walkDir(directory, RelativePath.EMPTY_ROOT, visitor, Specs.satisfyAll(), new AtomicBoolean(), false);
    }

    private void storeFileProperty(String propertyName, File file, TarOutputStream outputStream) {
        String path = "property-" + propertyName;
        storeFileEntry(file, path, file.lastModified(), file.length(), fileSystem.getUnixMode(file), outputStream);
    }

    private void storeDirectoryEntry(FileVisitDetails dirDetails, String propertyRoot, TarOutputStream outputStream) {
        String path = dirDetails.getRelativePath().getPathString();
        try {
            TarEntry entry = new TarEntry(propertyRoot + path + "/");
            entry.setModTime(dirDetails.getLastModified());
            entry.setMode(UnixStat.DIR_FLAG | dirDetails.getMode());
            outputStream.putNextEntry(entry);
            outputStream.closeEntry();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private void storeFileEntry(File file, String path, long lastModified, long size, int mode, TarOutputStream outputStream) {
        try {
            TarEntry entry = new TarEntry(path);
            entry.setModTime(lastModified);
            entry.setSize(size);
            entry.setMode(UnixStat.FILE_FLAG | mode);
            outputStream.putNextEntry(entry);
            Files.copy(file, outputStream);
            outputStream.closeEntry();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void unpack(TaskOutputsInternal taskOutputs, InputStream input) throws IOException {
        TarInputStream tarInput = new TarInputStream(input);
        try {
            unpack(taskOutputs, tarInput);
        } finally {
            tarInput.close();
        }
    }

    private void unpack(TaskOutputsInternal taskOutputs, TarInputStream tarInput) throws IOException {
        Map<String, TaskOutputFilePropertySpec> propertySpecs = Maps.uniqueIndex(taskOutputs.getFileProperties(), new Function<TaskFilePropertySpec, String>() {
            @Override
            public String apply(TaskFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
        TarEntry entry;
        Set<CacheableTaskOutputFilePropertySpec> seenProperties = Sets.newHashSet();
        while ((entry = tarInput.getNextEntry()) != null) {
            String name = entry.getName();
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
            // Create parent directories when we first see a property
            if (seenProperties.add(propertySpec)) {
                FileUtils.forceMkdir(specRoot.getParentFile());
            }

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
            if (!outputFile.setLastModified(entry.getModTime().getTime())) {
                throw new IOException(String.format("Could not set modification time for '%s'", outputFile));
            }
        }
    }
}
