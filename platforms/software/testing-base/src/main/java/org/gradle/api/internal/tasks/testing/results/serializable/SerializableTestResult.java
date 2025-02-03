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

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializedMetadata.SerializedMetadataElement;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;

/**
 * Represents a test result that can be stored for a long time (potentially across process invocations).
 *
 * <p>
 * This is different from {@link org.gradle.api.tasks.testing.TestResult} which contains no identifying information about the test that produced it, and is not designed to be stored.
 * Specifically, this class does not contain exception objects.
 * </p>
 */
@NonNullApi
public final class SerializableTestResult {
    public static Builder builder() {
        return new Builder();
    }

    @NonNullApi
    public static final class Builder {
        private String name;
        private String displayName;
        private TestResult.ResultType resultType;
        private Long startTime;
        private Long endTime;
        private final ImmutableList.Builder<SerializableFailure> failures = ImmutableList.builder();
        private final ImmutableList.Builder<SerializedMetadata> metadatas = ImmutableList.builder();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder resultType(TestResult.ResultType resultType) {
            this.resultType = resultType;
            return this;
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addFailure(SerializableFailure failure) {
            failures.add(failure);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addMetadata(SerializedMetadata metadata) {
            this.metadatas.add(metadata);
            return this;
        }

        public SerializableTestResult build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            if (displayName == null) {
                throw new IllegalStateException("displayName is required");
            }
            if (resultType == null) {
                throw new IllegalStateException("resultType is required");
            }
            if (startTime == null) {
                throw new IllegalStateException("startTime is required");
            }
            if (endTime == null) {
                throw new IllegalStateException("endTime is required");
            }
            return new SerializableTestResult(name, displayName, resultType, startTime, endTime, failures.build(), metadatas.build());
        }
    }

    @NonNullApi
    public static final class Serializer {
        private Serializer() { /* static util class is not instantiable */ }

        public static void serialize(SerializableTestResult result, Encoder encoder) throws IOException {
            encoder.writeString(result.name);
            encoder.writeString(result.displayName);
            encoder.writeSmallInt(result.resultType.ordinal());
            encoder.writeLong(result.startTime);
            encoder.writeLong(result.endTime);

            serializeFailures(result, encoder);
            serializeMetadatas(result, encoder);
        }

        public static SerializableTestResult deserialize(Decoder decoder) throws IOException {
            String name = decoder.readString();
            String displayName = decoder.readString();
            TestResult.ResultType resultType = TestResult.ResultType.values()[decoder.readSmallInt()];
            long startTime = decoder.readLong();
            long endTime = decoder.readLong();

            ImmutableList<SerializableFailure> failures = deserializeFailures(decoder);
            ImmutableList<SerializedMetadata> metadatas = deserializeMetadatas(decoder);

            return new SerializableTestResult(name, displayName, resultType, startTime, endTime, failures, metadatas);
        }

        private static void serializeFailures(SerializableTestResult result, Encoder encoder) throws IOException {
            encoder.writeSmallInt(result.failures.size());
            for (SerializableFailure failure : result.failures) {
                encoder.writeString(failure.getMessage());
                encoder.writeString(failure.getStackTrace());
                encoder.writeString(failure.getExceptionType());
            }
        }

        private static ImmutableList<SerializableFailure> deserializeFailures(Decoder decoder) throws IOException {
            ImmutableList.Builder<SerializableFailure> failures = ImmutableList.builder();
            int failureCount = decoder.readSmallInt();
            for (int i = 0; i < failureCount; i++) {
                String message = decoder.readString();
                String stackTrace = decoder.readString();
                String exceptionType = decoder.readString();
                failures.add(new SerializableFailure(message, stackTrace, exceptionType));
            }
            return failures.build();
        }

        private static void serializeMetadatas(SerializableTestResult result, Encoder encoder) throws IOException {
            encoder.writeSmallInt(result.metadatas.size());
            for (SerializedMetadata metadata : result.metadatas) {
                encoder.writeLong(metadata.getLogTime());
                encoder.writeSmallInt(metadata.getEntries().size());
                for (SerializedMetadataElement entry : metadata.getEntries()) {
                    encoder.writeString(entry.getKey());
                    encoder.writeBinary(entry.getSerializedValue());
                    encoder.writeString(entry.getValueType());
                }
            }
        }

        private static ImmutableList<SerializedMetadata> deserializeMetadatas(Decoder decoder) throws IOException {
            ImmutableList.Builder<SerializedMetadata> metadatas = ImmutableList.builder();
            int metadataCount = decoder.readSmallInt();
            for (int i = 0; i < metadataCount; i++) {
                long logTime = decoder.readLong();
                int entryCount = decoder.readSmallInt();
                ImmutableList.Builder<SerializedMetadataElement> entries = ImmutableList.builder();
                for (int j = 0; j < entryCount; j++) {
                    String key = decoder.readString();
                    byte[] value = decoder.readBinary();
                    String valueType = decoder.readString();
                    entries.add(new SerializedMetadataElement(key, value, valueType));
                }
                metadatas.add(new SerializedMetadata(logTime, entries.build()));
            }
            return metadatas.build();
        }
    }

    private final String name;
    private final String displayName;
    private final TestResult.ResultType resultType;
    private final long startTime;
    private final long endTime;
    private final ImmutableList<SerializableFailure> failures;
    private final ImmutableList<SerializedMetadata> metadatas;

    public SerializableTestResult(String name, String displayName, TestResult.ResultType resultType, long startTime, long endTime,
                                  ImmutableList<SerializableFailure> failures,
                                  ImmutableList<SerializedMetadata> metadatas) {
        this.name = name;
        this.displayName = displayName;
        this.resultType = resultType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.failures = failures;
        this.metadatas = metadatas;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TestResult.ResultType getResultType() {
        return resultType;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public ImmutableList<SerializableFailure> getFailures() {
        return failures;
    }

    public ImmutableList<SerializedMetadata> getMetadatas() {
        return metadatas;
    }
}
