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

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

/**
 * Opaque object that can be exchanged for output associated with a test result.
 *
 * <p>
 * Internally stores information that allows locating the output for a specific test result in an output events file.
 * </p>
 */
public final class OutputEntry {
    public static final Serializer<OutputEntry> SERIALIZER = new Serializer<OutputEntry>() {
        private static final byte DESTINATION_STDOUT = 0b01;
        private static final byte DESTINATION_STDERR = 0b10;

        @Override
        public OutputEntry read(Decoder decoder) throws IOException {
            long id = decoder.readSmallLong();
            byte destinations = decoder.readByte();
            long startStdout = NO_OUTPUT;
            long startStderr = NO_OUTPUT;
            long end = NO_OUTPUT;
            if ((destinations & DESTINATION_STDOUT) != 0) {
                startStdout = decoder.readSmallLong();
            }
            if ((destinations & DESTINATION_STDERR) != 0) {
                startStderr = decoder.readSmallLong();
            }
            if (destinations != 0) {
                end = decoder.readSmallLong();
            }
            return new OutputEntry(id, startStdout, startStderr, end);
        }

        @Override
        public void write(Encoder encoder, OutputEntry value) throws IOException {
            encoder.writeSmallLong(value.id);

            byte destinations = 0;
            if (value.startStdout != NO_OUTPUT) {
                destinations |= DESTINATION_STDOUT;
            }
            if (value.startStderr != NO_OUTPUT) {
                destinations |= DESTINATION_STDERR;
            }
            encoder.writeByte(destinations);

            if (value.startStdout != NO_OUTPUT) {
                encoder.writeSmallLong(value.startStdout);
            }
            if (value.startStderr != NO_OUTPUT) {
                encoder.writeSmallLong(value.startStderr);
            }
            if (destinations != 0) {
                encoder.writeSmallLong(value.end);
            }
        }
    };

    static final long NO_OUTPUT = -1;

    private static void validateRange(String name, long start, long end) {
        if (start != NO_OUTPUT) {
            if (start < 0) {
                throw new IllegalStateException("Invalid " + name + " start: " + start);
            }
            if (end < start) {
                throw new IllegalStateException("Range end " + end + " is before " + name + " start " + start);
            }
        }
    }

    final long id;
    final long startStdout;
    final long startStderr;
    final long end;

    OutputEntry(long id, long startStdout, long startStderr, long end) {
        if (id < 0) {
            throw new IllegalStateException("Invalid id: " + id);
        }
        validateRange("stdout", startStdout, end);
        validateRange("stderr", startStderr, end);
        this.id = id;
        this.startStdout = startStdout;
        this.startStderr = startStderr;
        this.end = end;
    }

    public boolean hasOutput() {
        return startStdout != NO_OUTPUT || startStderr != NO_OUTPUT;
    }
}
