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
import org.gradle.api.internal.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.worker.TestEventSerializer;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a test result that can be stored for a long time (potentially across process invocations).
 *
 * <p>
 * This is different from {@link org.gradle.api.tasks.testing.TestResult} which contains no identifying information about the test that produced it, and is not designed to be stored.
 * Specifically, this class does not contain exception objects.
 * </p>
 */
public final class SerializableTestResult {
    public static String getCombinedDisplayName(List<SerializableTestResult> testResults) {
        return testResults.stream()
            .map(SerializableTestResult::getDisplayName)
            .distinct()
            .collect(Collectors.joining(" / "));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        @Nullable
        private String name;
        @Nullable
        private String displayName;
        @Nullable
        private String className;
        @Nullable
        private String classDisplayName;
        private TestResult.@Nullable ResultType resultType;
        @Nullable
        private Long startTime;
        @Nullable
        private Long endTime;
        @Nullable
        private SerializableFailure assumptionFailure;
        private final ImmutableList.Builder<SerializableFailure> failures = ImmutableList.builder();
        private final ImmutableList.Builder<TestMetadataEvent> metadatas = ImmutableList.builder();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder className(@Nullable String className) {
            this.className = className;
            return this;
        }

        public Builder classDisplayName(@Nullable String classDisplayName) {
            this.classDisplayName = classDisplayName;
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

        public Builder assumptionFailure(@Nullable SerializableFailure failure) {
            this.assumptionFailure = failure;
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addFailure(SerializableFailure failure) {
            failures.add(failure);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Builder addMetadata(TestMetadataEvent metadata) {
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
            return new SerializableTestResult(name, displayName, className, classDisplayName, resultType, startTime, endTime, assumptionFailure, failures.build(), metadatas.build());
        }
    }

    public static final class Serializer {
        private Serializer() { /* static util class is not instantiable */ }
        private final static org.gradle.internal.serialize.Serializer<TestMetadataEvent> METADATA_EVENT_SERIALIZER = TestEventSerializer.create().build(TestMetadataEvent.class);

        public static void serialize(SerializableTestResult result, Encoder encoder) throws Exception {
            encoder.writeString(result.name);
            encoder.writeString(result.displayName);
            encoder.writeNullableString(result.className);
            encoder.writeNullableString(result.classDisplayName);
            encoder.writeSmallInt(result.resultType.ordinal());
            encoder.writeLong(result.startTime);
            encoder.writeLong(result.endTime);

            boolean hasAssumptionFailure = result.assumptionFailure != null;
            encoder.writeBoolean(hasAssumptionFailure);
            if (hasAssumptionFailure) {
                serializeFailure(encoder, result.assumptionFailure);
            }

            serializeFailures(result, encoder);
            serializeMetadatas(result, encoder);
        }

        public static SerializableTestResult deserialize(Decoder decoder) throws Exception {
            String name = decoder.readString();
            String displayName = decoder.readString();
            String className = decoder.readNullableString();
            String classDisplayName = decoder.readNullableString();
            TestResult.ResultType resultType = TestResult.ResultType.values()[decoder.readSmallInt()];
            long startTime = decoder.readLong();
            long endTime = decoder.readLong();

            SerializableFailure assumptionFailure = null;
            boolean hasAssumptionFailure = decoder.readBoolean();
            if (hasAssumptionFailure) {
                assumptionFailure = deserializeFailure(decoder);
            }

            ImmutableList<SerializableFailure> failures = deserializeFailures(decoder);
            ImmutableList<TestMetadataEvent> metadatas = deserializeMetadatas(decoder);

            return new SerializableTestResult(name, displayName, className, classDisplayName, resultType, startTime, endTime, assumptionFailure, failures, metadatas);
        }

        private static void serializeFailures(SerializableTestResult result, Encoder encoder) throws IOException {
            encoder.writeSmallInt(result.failures.size());
            for (SerializableFailure failure : result.failures) {
                serializeFailure(encoder, failure);
            }
        }

        private static ImmutableList<SerializableFailure> deserializeFailures(Decoder decoder) throws IOException {
            ImmutableList.Builder<SerializableFailure> failures = ImmutableList.builder();
            int failureCount = decoder.readSmallInt();
            for (int i = 0; i < failureCount; i++) {
                failures.add(deserializeFailure(decoder));
            }
            return failures.build();
        }

        private static void serializeFailure(Encoder encoder, SerializableFailure failure) throws IOException {
            encoder.writeString(failure.getMessage());
            encoder.writeString(failure.getStackTrace());
            encoder.writeString(failure.getExceptionType());
            encoder.writeInt(failure.getCauses().size());
            for (String cause : failure.getCauses()) {
                encoder.writeString(cause);
            }
        }

        private static SerializableFailure deserializeFailure(Decoder decoder) throws IOException {
            String message = decoder.readString();
            String stackTrace = decoder.readString();
            String exceptionType = decoder.readString();

            int causeCount = decoder.readInt();
            List<String> causes = new ArrayList<>(causeCount);
            for (int i = 0; i < causeCount; i++) {
                causes.add(decoder.readString());
            }
            return new SerializableFailure(message, stackTrace, exceptionType, causes);
        }

        private static void serializeMetadatas(SerializableTestResult result, Encoder encoder) throws Exception {
            encoder.writeInt(result.getMetadatas().size());
            for (TestMetadataEvent metadata : result.getMetadatas()) {
                METADATA_EVENT_SERIALIZER.write(encoder, metadata);
            }
        }

        private static ImmutableList<TestMetadataEvent> deserializeMetadatas(Decoder decoder) throws Exception {
            int metadatasCount = decoder.readInt();
            ImmutableList.Builder<TestMetadataEvent> metadatas = ImmutableList.builder();
            for (int i = 0; i < metadatasCount; i++) {
                metadatas.add(METADATA_EVENT_SERIALIZER.read(decoder));
            }
            return metadatas.build();
        }
    }

    private final String name;
    private final String displayName;
    @Nullable
    private final String className;
    @Nullable
    private final String classDisplayName;
    private final TestResult.ResultType resultType;
    private final long startTime;
    private final long endTime;
    @Nullable
    private final SerializableFailure assumptionFailure;
    private final ImmutableList<SerializableFailure> failures;
    private final ImmutableList<TestMetadataEvent> metadatas;

    public SerializableTestResult(
        String name,
        String displayName,
        @Nullable String className,
        @Nullable String classDisplayName,
        TestResult.ResultType resultType,
        long startTime,
        long endTime,
        @Nullable SerializableFailure assumptionFailure,
        ImmutableList<SerializableFailure> failures,
        ImmutableList<TestMetadataEvent> metadatas
    ) {
        this.name = name;
        this.displayName = displayName;
        this.className = className;
        this.classDisplayName = classDisplayName;
        this.resultType = resultType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.assumptionFailure = assumptionFailure;
        this.failures = failures;
        this.metadatas = metadatas;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getClassName() {
        return className;
    }

    @Nullable
    public String getClassDisplayName() {
        return classDisplayName;
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

    @Nullable
    public SerializableFailure getAssumptionFailure() {
        return assumptionFailure;
    }

    public ImmutableList<SerializableFailure> getFailures() {
        return failures;
    }

    public ImmutableList<TestMetadataEvent> getMetadatas() {
        return metadatas;
    }
}
