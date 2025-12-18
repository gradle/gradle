/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.worker;

import com.google.common.base.Throwables;
import org.gradle.api.internal.tasks.testing.AssertionFailureDetails;
import org.gradle.api.internal.tasks.testing.AssumptionFailureDetails;
import org.gradle.api.internal.tasks.testing.ClassTestDefinition;
import org.gradle.api.internal.tasks.testing.DefaultNestedTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultParameterizedTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestFailure;
import org.gradle.api.internal.tasks.testing.DefaultTestFailureDetails;
import org.gradle.api.internal.tasks.testing.DefaultTestFileAttachmentDataEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.DirectoryBasedTestDefinition;
import org.gradle.api.internal.tasks.testing.FileComparisonFailureDetails;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestFailureSerializationException;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.source.DefaultClassSource;
import org.gradle.api.internal.tasks.testing.source.DefaultClasspathResourceSource;
import org.gradle.api.internal.tasks.testing.source.DefaultDirectorySource;
import org.gradle.api.internal.tasks.testing.source.DefaultFilePosition;
import org.gradle.api.internal.tasks.testing.source.DefaultFileSource;
import org.gradle.api.internal.tasks.testing.source.DefaultMethodSource;
import org.gradle.api.internal.tasks.testing.source.DefaultNoSource;
import org.gradle.api.internal.tasks.testing.source.DefaultOtherSource;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestFailureDetails;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.source.ClassSource;
import org.gradle.api.tasks.testing.source.ClasspathResourceSource;
import org.gradle.api.tasks.testing.source.DirectorySource;
import org.gradle.api.tasks.testing.source.FilePosition;
import org.gradle.api.tasks.testing.source.FileSource;
import org.gradle.api.tasks.testing.source.MethodSource;
import org.gradle.api.tasks.testing.source.NoSource;
import org.gradle.api.tasks.testing.source.OtherSource;
import org.gradle.api.tasks.testing.source.TestSource;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SerializerRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NullMarked
public class TestEventSerializer {
    public static SerializerRegistry create() {
        BaseSerializerFactory factory = new BaseSerializerFactory();
        DefaultSerializerRegistry registry = new DefaultSerializerRegistry();
        registry.register(ClassTestDefinition.class, new ClassTestDefinitionSerializer());
        registry.register(DirectoryBasedTestDefinition.class, new DirectoryBasedTestDefinitionSerializer());
        registry.register(CompositeIdGenerator.CompositeId.class, new IdSerializer());
        registry.register(DefaultNestedTestSuiteDescriptor.class, new DefaultNestedTestSuiteDescriptorSerializer());
        registry.register(DefaultParameterizedTestDescriptor.class, new DefaultParameterizedTestDescriptorSerializer());
        registry.register(DefaultTestSuiteDescriptor.class, new DefaultTestSuiteDescriptorSerializer());
        registry.register(WorkerTestDefinitionProcessor.WorkerTestSuiteDescriptor.class, new WorkerTestSuiteDescriptorSerializer());
        registry.register(DefaultTestClassDescriptor.class, new DefaultTestClassDescriptorSerializer());
        registry.register(DefaultTestMethodDescriptor.class, new DefaultTestMethodDescriptorSerializer());
        registry.register(DefaultTestDescriptor.class, new DefaultTestDescriptorSerializer());

        registry.register(TestStartEvent.class, new TestStartEventSerializer());
        registry.register(TestCompleteEvent.class, new TestCompleteEventSerializer(factory));
        registry.register(DefaultTestOutputEvent.class, new DefaultTestOutputEventSerializer(factory));
        registry.register(TestMetadataEvent.class, new TestMetadataEventSerializer());

        Serializer<Throwable> throwableSerializer = factory.getSerializerFor(Throwable.class);
        registry.register(Throwable.class, throwableSerializer);
        registry.register(TestFailure.class, new DefaultTestFailureSerializer(throwableSerializer));

        return registry;
    }

    @NullMarked
    private static class NullableSerializer<T> implements Serializer<@Nullable T> {
        private final Serializer<T> serializer;

        private NullableSerializer(Serializer<T> serializer) {
            this.serializer = serializer;
        }

        @Override
        public @Nullable T read(Decoder decoder) throws Exception {
            if (!decoder.readBoolean()) {
                return null;
            }
            return serializer.read(decoder);
        }

        @Override
        public void write(Encoder encoder, @Nullable T value) throws Exception {
            encoder.writeBoolean(value != null);
            if (value != null) {
                serializer.write(encoder, value);
            }
        }
    }

    private static class IdSerializer implements Serializer<CompositeIdGenerator.CompositeId> {
        @Override
        public CompositeIdGenerator.CompositeId read(Decoder decoder) throws Exception {
            return new CompositeIdGenerator.CompositeId(decoder.readLong(), decoder.readLong());
        }

        @Override
        public void write(Encoder encoder, CompositeIdGenerator.CompositeId value) throws Exception {
            encoder.writeLong((Long) value.getScope());
            encoder.writeLong((Long) value.getId());
        }
    }

    private static class ClassTestDefinitionSerializer implements Serializer<ClassTestDefinition> {
        @Override
        public ClassTestDefinition read(Decoder decoder) throws Exception {
            return new ClassTestDefinition(decoder.readString());
        }

        @Override
        public void write(Encoder encoder, ClassTestDefinition value) throws Exception {
            encoder.writeString(value.getId());
        }
    }

    @NullMarked
    private static class DirectoryBasedTestDefinitionSerializer implements Serializer<DirectoryBasedTestDefinition> {
        @Override
        public DirectoryBasedTestDefinition read(Decoder decoder) throws Exception {
            return new DirectoryBasedTestDefinition(new File(decoder.readString()));
        }

        @Override
        public void write(Encoder encoder, DirectoryBasedTestDefinition value) throws Exception {
            encoder.writeString(value.getTestDefinitionsDir().getAbsolutePath());
        }
    }

    private static class TestStartEventSerializer implements Serializer<TestStartEvent> {
        final NullableSerializer<CompositeIdGenerator.CompositeId> idSerializer = new NullableSerializer<>(new IdSerializer());

        @Override
        public TestStartEvent read(Decoder decoder) throws Exception {
            long time = decoder.readLong();
            Object id = idSerializer.read(decoder);
            return new TestStartEvent(time, id);
        }

        @Override
        public void write(Encoder encoder, TestStartEvent value) throws Exception {
            encoder.writeLong(value.getStartTime());
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getParentId());
        }
    }

    @NullMarked
    private static class TestMetadataEventSerializer implements Serializer<TestMetadataEvent> {
        private final MapSerializer<String, String> mapSerializer = new MapSerializer<>(BaseSerializerFactory.STRING_SERIALIZER, BaseSerializerFactory.STRING_SERIALIZER);
        private final Serializer<Path> pathSerializer = BaseSerializerFactory.PATH_SERIALIZER;

        private static final int MAP_TYPE = 0;
        private static final int FILE_ATTACHMENT_TYPE = 1;

        @Override
        public TestMetadataEvent read(Decoder decoder) throws Exception {
            Instant logTime = Instant.ofEpochMilli(decoder.readLong());
            int type = decoder.readInt();
            switch (type) {
                case MAP_TYPE:
                    Map<String, String> keyValues = mapSerializer.read(decoder);
                    return new DefaultTestKeyValueDataEvent(logTime, keyValues);
                case FILE_ATTACHMENT_TYPE:
                    return new DefaultTestFileAttachmentDataEvent(logTime, pathSerializer.read(decoder), decoder.readNullableString());
            }
            throw new IllegalStateException("Unknown type of test metadata: " + type);
        }

        @Override
        public void write(Encoder encoder, TestMetadataEvent value) throws Exception {
            encoder.writeLong(value.getLogTime().toEpochMilli());

            if (value instanceof DefaultTestKeyValueDataEvent) {
                encoder.writeInt(MAP_TYPE);
                mapSerializer.write(encoder, ((DefaultTestKeyValueDataEvent) value).getValues());
            } else if (value instanceof DefaultTestFileAttachmentDataEvent) {
                encoder.writeInt(FILE_ATTACHMENT_TYPE);
                pathSerializer.write(encoder, ((DefaultTestFileAttachmentDataEvent) value).getPath());
                encoder.writeNullableString(((DefaultTestFileAttachmentDataEvent) value).getMediaType());
            }
        }
    }

    private static class TestCompleteEventSerializer implements Serializer<TestCompleteEvent> {
        private final NullableSerializer<TestResult.ResultType> typeSerializer;

        TestCompleteEventSerializer(BaseSerializerFactory factory) {
            typeSerializer = new NullableSerializer<>(factory.getSerializerFor(TestResult.ResultType.class));
        }

        @Override
        public TestCompleteEvent read(Decoder decoder) throws Exception {
            long endTime = decoder.readLong();
            TestResult.ResultType result = typeSerializer.read(decoder);
            return new TestCompleteEvent(endTime, result);
        }

        @Override
        public void write(Encoder encoder, TestCompleteEvent value) throws Exception {
            encoder.writeLong(value.getEndTime());
            typeSerializer.write(encoder, value.getResultType());
        }
    }

    private static class DefaultTestOutputEventSerializer implements Serializer<DefaultTestOutputEvent> {
        private final Serializer<TestOutputEvent.Destination> destinationSerializer;

        DefaultTestOutputEventSerializer(BaseSerializerFactory factory) {
            destinationSerializer = factory.getSerializerFor(TestOutputEvent.Destination.class);
        }

        @Override
        public DefaultTestOutputEvent read(Decoder decoder) throws Exception {
            long logTime = decoder.readLong();
            TestOutputEvent.Destination destination = destinationSerializer.read(decoder);
            String message = decoder.readString();
            return new DefaultTestOutputEvent(logTime, destination, message);
        }

        @Override
        public void write(Encoder encoder, DefaultTestOutputEvent value) throws Exception {
            encoder.writeLong(value.getLogTime());
            destinationSerializer.write(encoder, value.getDestination());
            encoder.writeString(value.getMessage());
        }
    }

    @NullMarked
    private static class DefaultTestFailureSerializer implements Serializer<TestFailure> {
        private final Serializer<Throwable> throwableSerializer;

        public DefaultTestFailureSerializer(Serializer<Throwable> throwableSerializer) {
            this.throwableSerializer = throwableSerializer;
        }

        @Override
        public TestFailure read(Decoder decoder) throws Exception {
            // Read raw throwable
            Throwable rawFailure = readThrowableCatchingFailure(decoder);

            // Read all causes
            int numOfCauses = decoder.readSmallInt();
            List<TestFailure> causes = new ArrayList<>(numOfCauses);
            for (int i = 0; i < numOfCauses; i++) {
                causes.add(read(decoder));
            }

            // Fields available to all details
            String message = decoder.readNullableString();
            String className = decoder.readString();
            String stacktrace = decoder.readString();

            // assumption failure
            boolean isAssumptionFailure = decoder.readBoolean();

            // assertion failure
            boolean isAssertionFailure = decoder.readBoolean();
            String expected = decoder.readNullableString();
            String actual = decoder.readNullableString();

            // file comparison failure
            boolean isFileComparisonFailure = decoder.readBoolean();

            int expectedContentSize = decoder.readInt();
            byte[] expectedContent;
            if (expectedContentSize == -1) {
                expectedContent = null;
            } else {
                expectedContent = new byte[expectedContentSize];
                decoder.readBytes(expectedContent);
            }

            int actualContentSize = decoder.readInt();
            byte[] actualContent;
            if (actualContentSize == -1) {
                actualContent = null;
            } else {
                actualContent = new byte[actualContentSize];
                decoder.readBytes(actualContent);
            }

            // Order is important here because a file comparison is _also_ an assertion failure
            final TestFailureDetails details;
            if (isFileComparisonFailure) {
                details = new FileComparisonFailureDetails(message, className, stacktrace, expected, actual, expectedContent, actualContent);
            } else if (isAssertionFailure) {
                details = new AssertionFailureDetails(message, className, stacktrace, expected, actual);
            } else if (isAssumptionFailure) {
                details = new AssumptionFailureDetails(message, className, stacktrace);
            } else if (rawFailure instanceof TestFailureSerializationException) {
                details = new DefaultTestFailureDetails(rawFailure.getMessage(), rawFailure.getClass().getName(), Throwables.getStackTraceAsString(rawFailure));
            } else {
                details = new DefaultTestFailureDetails(message, className, stacktrace);
            }

            return new DefaultTestFailure(rawFailure, details, causes);
        }

        /**
         * In the event that the exception thrown from the test cannot be successfully recreated, we capture the error
         * and put that in the test failure so that we pass on to the user whatever information we can about the test failure.
         */
        private Throwable readThrowableCatchingFailure(Decoder decoder) throws IOException {
            String rawFailureName = decoder.readString();
            Throwable rawFailure;
            try {
               rawFailure = throwableSerializer.read(decoder);
            } catch(Exception e) {
                rawFailure = new TestFailureSerializationException("An exception of type " + rawFailureName + " was thrown by the test, but Gradle was unable to recreate the exception in the build process", e);
            }
            return rawFailure;
        }

        @Override
        public void write(Encoder encoder, TestFailure value) throws Exception {
            // Write out raw throwable
            writeThrowableWithType(encoder, value, value.getRawFailure());

            // Write out all causes
            encoder.writeSmallInt(value.getCauses().size());
            for (TestFailure cause : value.getCauses()) {
                write(encoder, cause);
            }

            // Fields available to all details
            TestFailureDetails details = value.getDetails();
            encoder.writeNullableString(details.getMessage());
            encoder.writeString(details.getClassName());
            encoder.writeString(details.getStacktrace());

            // TODO: These could be optimized to only write out fields when necessary
            // based on the type of details

            // assumption failure
            encoder.writeBoolean(details.isAssumptionFailure());

            // assertion failure
            encoder.writeBoolean(details.isAssertionFailure());
            encoder.writeNullableString(details.getExpected());
            encoder.writeNullableString(details.getActual());

            // file comparison failure
            encoder.writeBoolean(details.isFileComparisonFailure());
            byte[] expectedContent = details.getExpectedContent();
            if (expectedContent == null) {
                encoder.writeInt(-1);
            } else {
                encoder.writeInt(expectedContent.length);
                encoder.writeBytes(expectedContent);
            }
            byte[] actualContent = details.getActualContent();
            if (actualContent == null) {
                encoder.writeInt(-1);
            } else {
                encoder.writeInt(actualContent.length);
                encoder.writeBytes(actualContent);
            }
        }

        /**
         * Serializes the exception thrown by the test and also writes the exception type so that if there is a failure recreating the exception, we can at least
         * provide the user with some information about what type of exception was thrown.
         */
        private void writeThrowableWithType(Encoder encoder, TestFailure value, Throwable rawFailure) throws Exception {
            encoder.writeString(rawFailure.getClass().getName());
            throwableSerializer.write(encoder, value.getRawFailure());
        }
    }

    private static class DefaultTestSuiteDescriptorSerializer implements Serializer<DefaultTestSuiteDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();

        @Override
        public DefaultTestSuiteDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String name = decoder.readString();
            return new DefaultTestSuiteDescriptor(id, name);
        }

        @Override
        public void write(Encoder encoder, DefaultTestSuiteDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getName());
        }
    }

    private static class DefaultNestedTestSuiteDescriptorSerializer implements Serializer<DefaultNestedTestSuiteDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();

        @Override
        public DefaultNestedTestSuiteDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String name = decoder.readString();
            String displayName = decoder.readString();
            CompositeIdGenerator.CompositeId parentId = idSerializer.read(decoder);
            return new DefaultNestedTestSuiteDescriptor(id, name, displayName, parentId);
        }

        @Override
        public void write(Encoder encoder, DefaultNestedTestSuiteDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getName());
            encoder.writeString(value.getDisplayName());
            idSerializer.write(encoder, value.getParentId());
        }
    }

    @NullMarked
    private static class DefaultParameterizedTestDescriptorSerializer implements Serializer<DefaultParameterizedTestDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();

        @Override
        public DefaultParameterizedTestDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String name = decoder.readString();
            String className = decoder.readNullableString();
            String displayName = decoder.readString();
            CompositeIdGenerator.CompositeId parentId = idSerializer.read(decoder);
            return new DefaultParameterizedTestDescriptor(id, name, className, displayName, parentId);
        }

        @Override
        public void write(Encoder encoder, DefaultParameterizedTestDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getName());
            encoder.writeNullableString(value.getClassName());
            encoder.writeString(value.getDisplayName());
            idSerializer.write(encoder, value.getParentId());
        }
    }

    private static class WorkerTestSuiteDescriptorSerializer implements Serializer<WorkerTestDefinitionProcessor.WorkerTestSuiteDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();

        @Override
        public WorkerTestDefinitionProcessor.WorkerTestSuiteDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String name = decoder.readString();
            return new WorkerTestDefinitionProcessor.WorkerTestSuiteDescriptor(id, name);
        }

        @Override
        public void write(Encoder encoder, WorkerTestDefinitionProcessor.WorkerTestSuiteDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getName());
        }
    }

    @NullMarked
    private static class TestSourceSerializer implements Serializer<TestSource> {

        Serializer<FilePosition> filePositionSerializer = new NullableSerializer<>(new FilePositionSerializer());

        @Override
        public TestSource read(Decoder decoder) throws Exception {
            int i = decoder.readSmallInt();
            if (i == 0) {
                return DefaultOtherSource.getInstance();
            } else if (i == 1) {
                return DefaultNoSource.getInstance();
            } else if (i == 2) {
                String absolutePath = decoder.readString();
                FilePosition filePosition = filePositionSerializer.read(decoder);
                return new DefaultFileSource(new File(absolutePath), filePosition);
            } else if (i == 3) {
                String absolutePath = decoder.readString();
                return new DefaultDirectorySource(new File(absolutePath));
            } else if (i == 4) {
                String classpathResourceName = decoder.readString();
                FilePosition position = filePositionSerializer.read(decoder);
                return new DefaultClasspathResourceSource(classpathResourceName, position);
            } else if (i == 5) {
                String className = decoder.readString();
                return new DefaultClassSource(className);
            } else if (i == 6) {
                String className = decoder.readString();
                String methodName = decoder.readString();
                return new DefaultMethodSource(className, methodName);
            } else {
                throw new IllegalArgumentException("Unknown TestSource type id: " + i);
            }
        }

        @Override
        public void write(Encoder encoder, TestSource value) throws Exception {
            if (value instanceof OtherSource) {
                encoder.writeSmallInt(0);
            } else if (value instanceof NoSource) {
                encoder.writeSmallInt(1);
            } else if (value instanceof FileSource) {
                encoder.writeSmallInt(2);
                FileSource fileSource = (FileSource) value;
                encoder.writeString(fileSource.getFile().getAbsolutePath());
                filePositionSerializer.write(encoder, fileSource.getPosition());
            } else if (value instanceof DirectorySource) {
                encoder.writeSmallInt(3);
                encoder.writeString(((DirectorySource) value).getFile().getAbsolutePath());
            } else if (value instanceof ClasspathResourceSource) {
                encoder.writeSmallInt(4);
                ClasspathResourceSource classpathResourceSource = (ClasspathResourceSource) value;
                encoder.writeString(classpathResourceSource.getClasspathResourceName());
                filePositionSerializer.write(encoder, classpathResourceSource.getPosition());
            } else if (value instanceof ClassSource) {
                encoder.writeSmallInt(5);
                ClassSource classSource = (ClassSource) value;
                encoder.writeString(classSource.getClassName());
            } else if (value instanceof MethodSource) {
                encoder.writeSmallInt(6);
                MethodSource methodSource = (MethodSource) value;
                encoder.writeString(methodSource.getClassName());
                encoder.writeString(methodSource.getMethodName());
            } else {
                throw new IllegalArgumentException("Unknown TestSource type: " + value.getClass().getName());
            }
        }
    }

    @NullMarked
    private static class FilePositionSerializer implements Serializer<FilePosition> {

        @Override
        public FilePosition read(Decoder decoder) throws Exception {
            int line = decoder.readInt();
            boolean hasColumn = decoder.readBoolean();
            if (hasColumn) {
                int column = decoder.readInt();
                return new DefaultFilePosition(line, Integer.valueOf(column));
            } else {
                return new DefaultFilePosition(line, null);
            }
        }

        @Override
        public void write(Encoder encoder, @Nullable FilePosition position) throws Exception {
            encoder.writeInt(position.getLine());
            if (position.getColumn() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeInt(position.getColumn());
            }
        }
    }

    private static class DefaultTestClassDescriptorSerializer implements Serializer<DefaultTestClassDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();

        @Override
        public DefaultTestClassDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String name = decoder.readString();
            String displayName = decoder.readString();
            return new DefaultTestClassDescriptor(id, name, displayName);
        }

        @Override
        public void write(Encoder encoder, DefaultTestClassDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getName());
            encoder.writeString(value.getDisplayName());
        }
    }

    private static class DefaultTestDescriptorSerializer implements Serializer<DefaultTestDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();
        final Serializer<TestSource> testSourceSerializer = new TestSourceSerializer();

        @Override
        public DefaultTestDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String className = decoder.readString();
            String classDisplayName = decoder.readString();
            String name = decoder.readString();
            String displayName = decoder.readString();
            TestSource source = testSourceSerializer.read(decoder);
            return new DefaultTestDescriptor(id, className, name, classDisplayName, displayName, source);
        }

        @Override
        public void write(Encoder encoder, DefaultTestDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getClassName());
            encoder.writeString(value.getClassDisplayName());
            encoder.writeString(value.getName());
            encoder.writeString(value.getDisplayName());
            testSourceSerializer.write(encoder, value.getSource());
        }
    }

    private static class DefaultTestMethodDescriptorSerializer implements Serializer<DefaultTestMethodDescriptor> {
        final Serializer<CompositeIdGenerator.CompositeId> idSerializer = new IdSerializer();

        @Override
        public DefaultTestMethodDescriptor read(Decoder decoder) throws Exception {
            Object id = idSerializer.read(decoder);
            String className = decoder.readString();
            String name = decoder.readString();
            return new DefaultTestMethodDescriptor(id, className, name);
        }

        @Override
        public void write(Encoder encoder, DefaultTestMethodDescriptor value) throws Exception {
            idSerializer.write(encoder, (CompositeIdGenerator.CompositeId) value.getId());
            encoder.writeString(value.getClassName());
            encoder.writeString(value.getName());
        }
    }
}
