/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes test output to an output events file. The file is simply a repeated sequence of
 * {@code <id> <output event>} entries. Offsets are stored with the result entries to allow reading
 * only the relevant parts of the file, though there may be unrelated output events in between.
 */
final class TestOutputWriter implements Closeable {
    private static final class OutputStarts {
        private long startStdout = OutputRanges.NO_OUTPUT;
        private long startStderr = OutputRanges.NO_OUTPUT;
    }

    /**
     * Encoder storing all output events.
     */
    private final KryoBackedEncoder outputEventsEncoder;
    private final Serializer<TestOutputEvent> testOutputEventSerializer;
    private final Map<Long, OutputStarts> outputEntryRangeStarts = new HashMap<>();

    public TestOutputWriter(Path outputEventsFile, Serializer<TestOutputEvent> testOutputEventSerializer) throws IOException {
        Files.deleteIfExists(outputEventsFile);
        this.outputEventsEncoder = new KryoBackedEncoder(Files.newOutputStream(outputEventsFile));
        this.testOutputEventSerializer = testOutputEventSerializer;
    }

    public void writeOutputEvent(long id, TestOutputEvent event) {
        // Assign the start of the output entry range if this is the first time we see this output id
        OutputStarts ranges = outputEntryRangeStarts.computeIfAbsent(id, i -> new OutputStarts());
        switch (event.getDestination()) {
            case StdOut:
                if (ranges.startStdout == OutputRanges.NO_OUTPUT) {
                    ranges.startStdout = outputEventsEncoder.getWritePosition();
                }
                break;
            case StdErr:
                if (ranges.startStderr == OutputRanges.NO_OUTPUT) {
                    ranges.startStderr = outputEventsEncoder.getWritePosition();
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown destination: " + event.getDestination());
        }
        try {
            outputEventsEncoder.writeLong(id);
            testOutputEventSerializer.write(
                outputEventsEncoder, (DefaultTestOutputEvent) event
            );
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public OutputRanges finishOutput(long id) {
        OutputStarts outputStarts = outputEntryRangeStarts.remove(id);
        return OutputRanges.of(
            outputStarts != null ? outputStarts.startStdout : OutputRanges.NO_OUTPUT,
            outputStarts != null ? outputStarts.startStderr : OutputRanges.NO_OUTPUT,
            outputStarts != null ? outputEventsEncoder.getWritePosition() : OutputRanges.NO_OUTPUT
        );
    }

    @Override
    public void close() {
        outputEventsEncoder.close();
    }
}
