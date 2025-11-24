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

import com.google.common.base.Throwables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.worker.TestEventSerializer;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.ExceptionSerializationUtil;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An object that can store test results and their outputs.
 */
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
    private final Path outputEventsFile;

    public SerializableTestResultStore(Path resultsDir) {
        this.serializedResultsFile = resultsDir.resolve("results-generic.bin");
        this.outputEventsFile = resultsDir.resolve("output-events.bin");
    }

    public Writer openWriter(int diskSkipLevels) throws IOException {
        return new Writer(serializedResultsFile, outputEventsFile, diskSkipLevels);
    }

    public static final class Writer implements Closeable, TestListenerInternal {
        private static boolean isRoot(TestDescriptorInternal descriptor) {
            return descriptor.getParent() == null;
        }

        private static int depth(TestDescriptorInternal descriptor) {
            int depth = 0;
            while (descriptor.getParent() != null) {
                depth++;
                descriptor = descriptor.getParent();
            }
            return depth;
        }

        private static final long ROOT_ID = 1;

        private final Map<Object, Long> assignedIds = new HashMap<>();
        private final Set<Object> flatteningIds;
        private final List<TestDescriptorInternal> extraFlattenedDescriptors;
        private final List<TestResult> extraFlattenedResults;
        private final Path serializedResultsFile;
        private final int diskSkipLevels;
        private final Path temporaryResultsFile;
        /**
         * Encoder storing the serialized test results.
         */
        private final KryoBackedEncoder resultsEncoder;
        private final TestOutputWriter outputWriter;
        private long nextId = 1;

        // Map from testDescriptor -> Serialized metadata associated with that descriptor
        private final Multimap<TestDescriptorInternal, TestMetadataEvent> metadatas = LinkedHashMultimap.create();

        private Writer(Path serializedResultsFile, Path outputEventsFile, int diskSkipLevels) throws IOException {
            this.serializedResultsFile = serializedResultsFile;
            this.diskSkipLevels = diskSkipLevels;
            // Use constants to avoid allocating empty collections if flattening is not enabled
            flatteningIds = isDiskSkipEnabled() ? new HashSet<>() : Collections.emptySet();
            extraFlattenedDescriptors = isDiskSkipEnabled() ? new ArrayList<>() : Collections.emptyList();
            extraFlattenedResults = isDiskSkipEnabled() ? new ArrayList<>() : Collections.emptyList();
            Files.createDirectories(serializedResultsFile.getParent());
            temporaryResultsFile = Files.createTempFile(serializedResultsFile.getParent(), "in-progress-results-generic", ".bin");
            resultsEncoder = new KryoBackedEncoder(Files.newOutputStream(temporaryResultsFile));
            Serializer<TestOutputEvent> testOutputEventSerializer = TestEventSerializer.create().build(TestOutputEvent.class);
            try {
                resultsEncoder.writeSmallInt(STORE_VERSION);
                outputWriter = new TestOutputWriter(outputEventsFile, testOutputEventSerializer);
            } catch (Throwable t) {
                // Ensure we don't leak the encoder if we fail to do operations in the constructor
                try {
                    resultsEncoder.close();
                } catch (Throwable t2) {
                    t.addSuppressed(t2);
                }
                throw t;
            }
        }

        private boolean isDiskSkipEnabled() {
            return diskSkipLevels > 0;
        }

        @Override
        public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
            if (isDiskSkipEnabled() && !isRoot(testDescriptor) && depth(testDescriptor) <= diskSkipLevels) {
                flatteningIds.add(testDescriptor.getId());
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
            if (isDiskSkipEnabled()) {
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
                .className(testDescriptor.getClassName())
                .classDisplayName(testDescriptor.getClassDisplayName())
                .startTime(testResult.getStartTime())
                .endTime(testResult.getEndTime())
                .resultType(testResult.getResultType());

            if (testResult.getAssumptionFailure() != null) {
                testNodeBuilder.assumptionFailure(convertToSerializableFailure(testResult.getAssumptionFailure()));
            }

            for (TestFailure failure : testResult.getFailures()) {
                testNodeBuilder.addFailure(convertToSerializableFailure(failure));
            }

            for (TestMetadataEvent metadata : metadatas.removeAll(testDescriptor)) {
                testNodeBuilder.addMetadata(metadata);
            }

            if (isDiskSkipEnabled() && isRoot(testDescriptor)) {
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
                    for (TestMetadataEvent metadata : metadatas.removeAll(flattenedDescriptor)) {
                        testNodeBuilder.addMetadata(metadata);
                    }
                }
                extraFlattenedDescriptors.clear();
            }

            // We remove the id here since no further events should come for this test, and it won't be needed as a parent id anymore
            long id = assignedIds.remove(testDescriptor.getId());
            resultsEncoder.writeSmallLong(id);
            try {
                OutputRanges outputRanges = outputWriter.finishOutput(id);
                OutputRanges.SERIALIZER.write(resultsEncoder, outputRanges);
                SerializableTestResult.Serializer.serialize(testNodeBuilder.build(), resultsEncoder);
            } catch (Exception e) {
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
            if (!isDiskSkipEnabled() || isRoot(testDescriptor)) {
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
            // Build message in the same way as Throwable.toString()
            String message = failure.getDetails().getClassName();
            if (failure.getDetails().getMessage() != null) {
                message += ": " + failure.getDetails().getMessage();
            }
            List<String> convertedCauses = ExceptionSerializationUtil.extractCauses(failure.getRawFailure()).stream()
                .map(Throwables::getStackTraceAsString)
                .collect(Collectors.toList());

            return new SerializableFailure(
                message,
                failure.getDetails().getStacktrace(),
                failure.getDetails().getClassName(),
                convertedCauses
            );
        }

        @Override
        public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
            long outputId;
            // Log to the root of the output zip file if this is a flattened test
            if (isDiskSkipEnabled() && flatteningIds.contains(testDescriptor.getId())) {
                outputId = ROOT_ID;
            } else {
                outputId = assignedIds.get(testDescriptor.getId());
            }
            outputWriter.writeOutputEvent(outputId, event);
        }

        @Override
        public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {
            metadatas.put(testDescriptor, event);
        }

        @Override
        public void close() throws IOException {
            try {
                // Write a 0 id to terminate the file
                resultsEncoder.writeSmallLong(0);
            } finally {
                CompositeStoppable.stoppable(resultsEncoder, outputWriter).stop();
            }
            // Move the temporary results file to the final location, if successful
            Files.move(temporaryResultsFile, serializedResultsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public boolean hasResults() {
        if (Files.exists(serializedResultsFile) && Files.exists(outputEventsFile)) {
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

    /**
     * Processor for test results.
     */
    @FunctionalInterface
    public interface ResultProcessor {
        /**
         * Process a single test result.
         *
         * @param id the id of the result
         * @param parentId the id of the parent result, or {@code null} if this is a root result
         * @param result the test result
         * @param outputRanges the output ranges for the result
         * @throws IOException if an error occurs while processing the result
         */
        void process(long id, @Nullable Long parentId, SerializableTestResult result, OutputRanges outputRanges) throws IOException;
    }

    /**
     * Exists for backwards compatibility with TestFilesCleanupService.
     *
     * Mirrors the old structure of OutputTrackedResult that was used by TestFilesCleanupService.
     * Doesn't need to be the original class since the type wasn't explicitly referenced.
     */
    public static final class FacadeForOutputTrackedResult {
        private final SerializableTestResult innerResult;

        public FacadeForOutputTrackedResult(SerializableTestResult innerResult) {
            this.innerResult = innerResult;
        }

        public SerializableTestResult getInnerResult() {
            return innerResult;
        }
    }

    /**
     * Exists for backwards compatibility with TestFilesCleanupService.
     */
    @SuppressWarnings("unused")
    public void forEachResult(Consumer<FacadeForOutputTrackedResult> consumer) throws Exception {
        forEachResult((id, parentId, result, outputRanges) ->
            consumer.accept(new FacadeForOutputTrackedResult(result))
        );
    }

    /**
     * Visit every result in the store. Parents are visited <em>AFTER</em> their children, but not necessarily in a breadth-first or depth-first order.
     * The processor is called once for each result.
     *
     * @param processor the processor to call for each result
     * @throws IOException if an error occurs while reading the results
     */
    public void forEachResult(ResultProcessor processor) throws Exception {
        try (KryoBackedDecoder resultsDecoder = openAndInitializeDecoder()) {
            while (true) {
                long id = resultsDecoder.readSmallLong();
                if (id == 0) {
                    break;
                }
                if (id < 0) {
                    throw new IllegalStateException("Invalid result id: " + id);
                }
                OutputRanges ranges;
                try {
                    ranges = OutputRanges.SERIALIZER.read(resultsDecoder);
                } catch (Exception e) {
                    if (e instanceof IOException) {
                        throw e;
                    }
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                SerializableTestResult testResult = SerializableTestResult.Serializer.deserialize(resultsDecoder);
                long parentId = resultsDecoder.readSmallLong();
                if (parentId < 0) {
                    throw new IllegalStateException("Invalid parent id: " + parentId);
                }
                Long parentIdObj = parentId == 0 ? null : parentId;
                processor.process(id, parentIdObj, testResult, ranges);
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

    public TestOutputReader createOutputReader(Serializer<TestOutputEvent> testOutputEventSerializer) {
        return new TestOutputReader(outputEventsFile, testOutputEventSerializer);
    }
}
