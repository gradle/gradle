/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results.serializable;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.io.input.NullReader;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.PlaceholderExceptionSupport;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * An object that can store test results and their outputs.
 */
@NullMarked
public final class SerializableTestResultStore {

    /**
     * Version for the results and output files.
     *
     * <p>
     * This version is only written to the results file, as the two are written together.
     * </p>
     */
    private static final int STORE_VERSION = 1;

    private final Path serializedResultsFile;
    private final Path outputZipFile;

    public SerializableTestResultStore(Path resultsDir) {
        this.serializedResultsFile = resultsDir.resolve("results-generic.bin");
        this.outputZipFile = resultsDir.resolve("output-generic.zip");
    }

    public static boolean isGenericTestResults(File resultDir) {
        return Files.exists(resultDir.toPath().resolve("results-generic.bin"));
    }

    public Writer openWriter() throws IOException {
        return new Writer(serializedResultsFile, outputZipFile);
    }

    @NullMarked
    public static final class Writer implements Closeable, TestListenerInternal {
        private final Map<Object, Long> assignedIds = new HashMap<>();
        private final Path serializedResultsFile;
        private final Path temporaryResultsFile;
        private final KryoBackedEncoder resultsEncoder;
        private final FileSystem outputZipFileSystem;
        private long nextId = 1;

        // Map from testDescriptor -> Serialized metadata associated with that descriptor
        private final Multimap<TestDescriptorInternal, SerializedMetadata> metadatas = LinkedHashMultimap.create();

        private Writer(Path serializedResultsFile, Path outputZipFile) throws IOException {
            this.serializedResultsFile = serializedResultsFile;
            Files.createDirectories(serializedResultsFile.getParent());
            temporaryResultsFile = Files.createTempFile(serializedResultsFile.getParent(), "in-progress-results-generic", ".bin");
            resultsEncoder = new KryoBackedEncoder(Files.newOutputStream(temporaryResultsFile));
            resultsEncoder.writeSmallInt(STORE_VERSION);
            try {
                // Truncate existing output zip file
                new ZipOutputStream(Files.newOutputStream(outputZipFile)).close();
                try {
                    outputZipFileSystem = FileSystems.newFileSystem(URI.create("jar:" + outputZipFile.toUri()), Collections.emptyMap());
                } catch (FileSystemAlreadyExistsException e) {
                    throw new InvalidUserCodeException("A previous file system exists for " + outputZipFile + ", which likely means that a previous test reporter was not closed", e);
                }
            } catch (Throwable t) {
                // Ensure we don't leak the encoder if we fail to open the output zip file
                try {
                    resultsEncoder.close();
                } catch (Throwable t2) {
                    t.addSuppressed(t2);
                }
                throw t;
            }
        }

        @Override
        public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
            long id = nextId++;
            assignedIds.put(testDescriptor.getId(), id);
        }

        @Override
        public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
            SerializableTestResult.Builder testNodeBuilder = SerializableTestResult.builder()
                .name(testDescriptor.getName())
                .displayName(testDescriptor.getDisplayName())
                .startTime(testResult.getStartTime())
                .endTime(testResult.getEndTime())
                .resultType(testResult.getResultType());

            for (Throwable throwable : testResult.getExceptions()) {
                testNodeBuilder.addFailure(new SerializableFailure(failureMessage(throwable), stackTrace(throwable), exceptionClassName(throwable)));
            }

            for (SerializedMetadata metadata : metadatas.get(testDescriptor)) {
                testNodeBuilder.addMetadata(metadata);
            }

            // We remove the id here since no further events should come for this test, and it won't be needed as a parent id anymore
            long id = assignedIds.remove(testDescriptor.getId());
            resultsEncoder.writeSmallLong(id);
            try {
                SerializableTestResult.Serializer.serialize(testNodeBuilder.build(), resultsEncoder);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (testDescriptor.getParent() != null) {
                resultsEncoder.writeSmallLong(assignedIds.get(testDescriptor.getParent().getId()));
            } else {
                resultsEncoder.writeSmallLong(0);
            }
        }

        private static String failureMessage(Throwable throwable) {
            try {
                return throwable.toString();
            } catch (Throwable t) {
                String exceptionClassName = exceptionClassName(throwable);
                return String.format("Could not determine failure message for exception of type %s: %s",
                    exceptionClassName, t);
            }
        }

        private static String exceptionClassName(Throwable throwable) {
            return throwable instanceof PlaceholderExceptionSupport ? ((PlaceholderExceptionSupport) throwable).getExceptionClassName() : throwable.getClass().getName();
        }

        private static String stackTrace(Throwable throwable) {
            try {
                return getStacktrace(throwable);
            } catch (Throwable t) {
                return getStacktrace(t);
            }
        }

        private static String getStacktrace(Throwable throwable) {
            StringWriter writer = new StringWriter();
            try (PrintWriter printWriter = new PrintWriter(writer)) {
                throwable.printStackTrace(printWriter);
            }
            return writer.toString();
        }

        @Override
        public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
            Path file = outputZipFileSystem.getPath(Long.toString(assignedIds.get(testDescriptor.getId())), event.getDestination().name());
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, event.getMessage().getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new RuntimeException("Could not write output file '" + file + "'", e);
            }
        }

        @Override
        public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {
            metadatas.put(testDescriptor, new SerializedMetadata(event.getLogTime(), event.getValues()));
        }

        @Override
        public void close() throws IOException {
            try {
                // Write a 0 id to terminate the file
                resultsEncoder.writeSmallLong(0);
            } finally {
                CompositeStoppable.stoppable(resultsEncoder, outputZipFileSystem).stop();
                // Move the temporary results file to the final location
                Files.move(temporaryResultsFile, serializedResultsFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public boolean hasResults() {
        if (Files.exists(serializedResultsFile) && Files.exists(outputZipFile)) {
            // Inspect the results file, read first ID to see if there are any results
            try (KryoBackedDecoder decoder = openAndInitializeDecoder()) {
                return decoder.readSmallLong() != 0;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return false;
        }
    }

    @NullMarked
    public static final class OutputTrackedResult {
        private final long id;
        private final SerializableTestResult testResult;
        private final long parentId;

        private OutputTrackedResult(long id, SerializableTestResult testResult, long parentId) {
            this.id = id;
            this.testResult = testResult;
            this.parentId = parentId;
        }

        public SerializableTestResult getInnerResult() {
            return testResult;
        }

        public long getOutputId() {
            return id;
        }

        public OptionalLong getParentOutputId() {
            return parentId == 0 ? OptionalLong.empty() : OptionalLong.of(parentId);
        }
    }

    /**
     * Visit every result in the store. Parents are visited <em>AFTER</em> their children, but not necessarily in a breadth-first or depth-first order.
     * The action is called once for each result.
     *
     * @param action the action to perform on each result
     * @throws IOException if an error occurs while reading the results
     */
    public void forEachResult(Consumer<? super OutputTrackedResult> action) throws IOException {
        try (KryoBackedDecoder resultsDecoder = openAndInitializeDecoder()) {
            while (true) {
                long id = resultsDecoder.readSmallLong();
                if (id == 0) {
                    break;
                }
                if (id < 0) {
                    throw new IllegalStateException("Invalid id: " + id);
                }
                SerializableTestResult testResult = SerializableTestResult.Serializer.deserialize(resultsDecoder);
                long parentId = resultsDecoder.readSmallLong();
                if (parentId < 0) {
                    throw new IllegalStateException("Invalid parent id: " + parentId);
                }
                action.accept(new OutputTrackedResult(id, testResult, parentId));
            }
        }
    }

    private KryoBackedDecoder openAndInitializeDecoder() throws IOException {
        KryoBackedDecoder decoder = new KryoBackedDecoder(Files.newInputStream(serializedResultsFile));
        try {
            int version = decoder.readSmallInt();
            if (version != STORE_VERSION) {
                throw new IOException("Unsupported version: " + version);
            }
        } catch (Throwable t) {
            // Make sure we close the decoder if we fail to initialize it
            try {
                decoder.close();
            } catch (Throwable t2) {
                t.addSuppressed(t2);
            }
            throw t;
        }
        return decoder;
    }

    public OutputReader openOutputReader() throws IOException {
        return new OutputReader(outputZipFile);
    }

    @NullMarked
    public static final class OutputReader implements Closeable {
        private final ZipFile outputZipFile;

        private OutputReader(Path outputZipFile) throws IOException {
            this.outputZipFile = new ZipFile(outputZipFile.toFile());
        }

        @Nullable
        private ZipEntry getEntry(long id, TestOutputEvent.Destination destination) {
            return outputZipFile.getEntry(id + "/" + destination.name());
        }

        public boolean hasOutput(long id, TestOutputEvent.Destination destination) {
            return getEntry(id, destination) != null;
        }

        public java.io.Reader getOutput(long id, TestOutputEvent.Destination destination) {
            ZipEntry entry = getEntry(id, destination);
            if (entry == null) {
                // Map no entry to no output
                return new NullReader();
            }
            try {
                return new InputStreamReader(outputZipFile.getInputStream(entry), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Could not read output entry '" + entry.getName() + "'", e);
            }
        }

        @Override
        public void close() throws IOException {
            outputZipFile.close();
        }
    }
}
