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
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
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

    public Writer openWriter(boolean skipFirstLevelOnDisk) throws IOException {
        return new Writer(serializedResultsFile, outputZipFile, skipFirstLevelOnDisk);
    }

    @NullMarked
    public static final class Writer implements Closeable, TestListenerInternal {
        private static boolean isRoot(TestDescriptorInternal descriptor) {
            return descriptor.getParent() == null;
        }

        private static final long ROOT_ID = 1;

        private final Map<Object, Long> assignedIds = new HashMap<>();
        private final Set<Object> flatteningIds;
        private final List<TestDescriptorInternal> extraFlattenedDescriptors;
        private final List<TestResult> extraFlattenedResults;
        private final Path serializedResultsFile;
        private final boolean skipFirstLevelOnDisk;
        private final Path temporaryResultsFile;
        private final KryoBackedEncoder resultsEncoder;
        private final FileSystem outputZipFileSystem;
        private long nextId = 1;

        // Map from testDescriptor -> Serialized metadata associated with that descriptor
        private final Multimap<TestDescriptorInternal, SerializedMetadata> metadatas = LinkedHashMultimap.create();

        private Writer(Path serializedResultsFile, Path outputZipFile, boolean skipFirstLevelOnDisk) throws IOException {
            this.serializedResultsFile = serializedResultsFile;
            this.skipFirstLevelOnDisk = skipFirstLevelOnDisk;
            // Use constants to avoid allocating empty collections if flattening is not enabled
            flatteningIds = skipFirstLevelOnDisk ? new HashSet<>() : Collections.emptySet();
            extraFlattenedDescriptors = skipFirstLevelOnDisk ? new ArrayList<>() : Collections.emptyList();
            extraFlattenedResults = skipFirstLevelOnDisk ? new ArrayList<>() : Collections.emptyList();
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
            if (skipFirstLevelOnDisk) {
                TestDescriptorInternal parent = testDescriptor.getParent();
                if (!isRoot(testDescriptor) && isRoot(parent)) {
                    // parent is the root, flatten here
                    flatteningIds.add(testDescriptor.getId());
                }
            }
            long id = nextId++;
            // Sanity check, shouldn't happen in practice
            if (id == ROOT_ID) {
                if (!isRoot(testDescriptor)) {
                    throw new IllegalStateException("The first test descriptor must be the root, but got: " + testDescriptor);
                }
            }
            assignedIds.put(testDescriptor.getId(), id);
        }

        @Override
        public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
            if (skipFirstLevelOnDisk) {
                // Attach flattened results to the root if this is a flattened node
                if (flatteningIds.contains(testDescriptor.getId())) {
                    extraFlattenedDescriptors.add(testDescriptor);
                    extraFlattenedResults.add(testResult);
                    return;
                }
            }

            SerializableTestResult.Builder testNodeBuilder = SerializableTestResult.builder()
                .name(testDescriptor.getName())
                .displayName(testDescriptor.getDisplayName())
                .startTime(testResult.getStartTime())
                .endTime(testResult.getEndTime())
                .resultType(testResult.getResultType());

            if (testResult.getAssumptionFailure() != null) {
                testNodeBuilder.assumptionFailure(convertToSerializableFailure(testResult.getAssumptionFailure()));
            }

            for (TestFailure failure : testResult.getFailures()) {
                testNodeBuilder.addFailure(convertToSerializableFailure(failure));
            }

            for (SerializedMetadata metadata : metadatas.removeAll(testDescriptor)) {
                testNodeBuilder.addMetadata(metadata);
            }

            if (skipFirstLevelOnDisk && isRoot(testDescriptor)) {
                // Attach extra flattened results to the root node
                boolean hasAssumptionFailure = testResult.getAssumptionFailure() != null;
                for (TestResult flattenedResult : extraFlattenedResults) {
                    if (flattenedResult.getAssumptionFailure() != null) {
                        if (hasAssumptionFailure) {
                            throw new IllegalStateException("Multiple assumption failures would need to be handled, but only one is supported: " + testDescriptor);
                        }
                        hasAssumptionFailure = true;

                        testNodeBuilder.assumptionFailure(convertToSerializableFailure(flattenedResult.getAssumptionFailure()));
                    }

                    for (TestFailure failure : flattenedResult.getFailures()) {
                        testNodeBuilder.addFailure(convertToSerializableFailure(failure));
                    }
                }
                extraFlattenedResults.clear();

                for (TestDescriptorInternal flattenedDescriptor : extraFlattenedDescriptors) {
                    for (SerializedMetadata metadata : metadatas.removeAll(flattenedDescriptor)) {
                        testNodeBuilder.addMetadata(metadata);
                    }
                }
                extraFlattenedDescriptors.clear();
            }

            // We remove the id here since no further events should come for this test, and it won't be needed as a parent id anymore
            long id = assignedIds.remove(testDescriptor.getId());
            resultsEncoder.writeSmallLong(id);
            try {
                SerializableTestResult.Serializer.serialize(testNodeBuilder.build(), resultsEncoder);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            TestDescriptorInternal parent = getFlattenedParent(testDescriptor);
            if (parent != null) {
                Long parentId = assignedIds.get(parent.getId());
                if (parentId == null) {
                    throw new IllegalStateException("No id found for test descriptor: " + parent);
                }
                resultsEncoder.writeSmallLong(parentId);
            } else {
                resultsEncoder.writeSmallLong(0);
            }
        }

        @Nullable
        private TestDescriptorInternal getFlattenedParent(TestDescriptorInternal testDescriptor) {
            if (!skipFirstLevelOnDisk || isRoot(testDescriptor)) {
                return testDescriptor.getParent();
            }
            TestDescriptorInternal parent = testDescriptor.getParent();
            assert parent != null : "Non-root test descriptor should always have a parent: " + testDescriptor.getDisplayName() + " (id: " + testDescriptor.getId() + ")";
            while (flatteningIds.contains(parent.getId())) {
                parent = parent.getParent();
                if (parent == null) {
                    // The root is always unflattened, so we should never reach here
                    throw new AssertionError(
                        "Parent of a flattened test descriptor should not be null: " + testDescriptor.getDisplayName() + " (id: " + testDescriptor.getId() + ")"
                    );
                }
            }
            return parent;
        }

        private static SerializableFailure convertToSerializableFailure(TestFailure failure) {
            String message = failure.getDetails().getMessage();
            if (message == null) {
                // Matching Throwable.toString() behavior, use the class name if no message is provided
                message = failure.getDetails().getClassName();
            }
            return new SerializableFailure(
                message,
                failure.getDetails().getStacktrace(),
                failure.getDetails().getClassName()
            );
        }

        @Override
        public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
            long outputId;
            // Log to the root of the output zip file if this is a flattened test
            if (skipFirstLevelOnDisk && flatteningIds.contains(testDescriptor.getId())) {
                outputId = ROOT_ID;
            } else {
                outputId = assignedIds.get(testDescriptor.getId());
            }
            Path file = outputZipFileSystem.getPath(Long.toString(outputId), event.getDestination().name());
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
                throw UncheckedException.throwAsUncheckedException(e);
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
