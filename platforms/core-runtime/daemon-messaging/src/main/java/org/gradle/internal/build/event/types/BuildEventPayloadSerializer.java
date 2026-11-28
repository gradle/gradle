/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.internal.build.event.types.test.source.DefaultClassSource;
import org.gradle.internal.build.event.types.test.source.DefaultClasspathResourceSource;
import org.gradle.internal.build.event.types.test.source.DefaultDirectorySource;
import org.gradle.internal.build.event.types.test.source.DefaultFileSource;
import org.gradle.internal.build.event.types.test.source.DefaultMethodSource;
import org.gradle.internal.build.event.types.test.source.DefaultNoSource;
import org.gradle.internal.build.event.types.test.source.DefaultOtherSource;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.InternalProblemGroup;
import org.gradle.tooling.internal.protocol.InternalProblemId;
import org.gradle.tooling.internal.protocol.InternalProblemSummary;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.protocol.events.InternalFilePosition;
import org.gradle.tooling.internal.protocol.events.InternalJavaCompileTaskOperationResult.InternalAnnotationProcessorResult;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestOutputResult;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalProblemDetailsVersion2;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;
import org.gradle.tooling.internal.protocol.test.source.InternalTestSource;
import org.gradle.tooling.internal.provider.PhasedBuildActionResult;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.serialization.SerializedPayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.StreamedValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializes the payload of a build event sent from the daemon to the client.
 */
@NullMarked
public class BuildEventPayloadSerializer implements Serializer<Object> {
    private static final int NULL = 0;
    private static final int BACK_REFERENCE = 1;
    private static final int FIRST_TYPE_TAG = 2;

    private static final class Codecs {
        static final Map<Class<?>, Codec<?>> BY_TYPE = new HashMap<Class<?>, Codec<?>>();
        static final List<Codec<?>> BY_TAG = new ArrayList<Codec<?>>();

        static {
            registerProgressEvents();
            registerDescriptors();
            registerResults();
            registerFailures();
            registerProblems();
            registerPlugins();
            registerTestSources();
            registerActionResults();
        }
    }

    private final SerializedPayloadSerializer payloadSerializer = new SerializedPayloadSerializer();

    @Override
    public void write(Encoder encoder, Object value) throws Exception {
        new Writer(encoder, payloadSerializer).writeObject(value);
    }

    @Override
    public Object read(Decoder decoder) throws Exception {
        Object value = new Reader(decoder, payloadSerializer).readNullableObject(Object.class);
        if (value == null) {
            throw new IllegalArgumentException("Build event payload must not be null");
        }
        return value;
    }

    private static void registerProgressEvents() {
        register(DefaultOperationStartedProgressEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
            },
            in -> new DefaultOperationStartedProgressEvent(
                in.readLong(),
                in.readObject(DefaultOperationDescriptor.class)
            )
        );
        register(DefaultOperationFinishedProgressEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeObject(v.getResult());
            },
            in -> new DefaultOperationFinishedProgressEvent(
                in.readLong(),
                in.readObject(DefaultOperationDescriptor.class),
                in.readObject(AbstractOperationResult.class)
            )
        );
        register(DefaultTaskStartedProgressEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
            },
            in -> new DefaultTaskStartedProgressEvent(
                in.readLong(),
                in.readObject(DefaultTaskDescriptor.class)
            )
        );
        register(DefaultTaskFinishedProgressEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeObject(v.getResult());
            },
            in -> new DefaultTaskFinishedProgressEvent(
                in.readLong(),
                in.readObject(DefaultTaskDescriptor.class),
                in.readObject(AbstractTaskResult.class)
            )
        );
        register(DefaultTestStartedProgressEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
            },
            in -> new DefaultTestStartedProgressEvent(
                in.readLong(),
                in.readObject(InternalTestDescriptor.class)
            )
        );
        register(DefaultTestFinishedProgressEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeObject(v.getResult());
            },
            in -> new DefaultTestFinishedProgressEvent(
                in.readLong(),
                in.readObject(InternalTestDescriptor.class),
                in.readObject(AbstractTestResult.class)
            )
        );
        register(DefaultTestOutputEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeObject(v.getResult());
            },
            in -> new DefaultTestOutputEvent(
                in.readLong(),
                in.readObject(InternalTestOutputDescriptor.class),
                in.readObject(InternalTestOutputResult.class)
            )
        );
        register(DefaultTestKeyValueDataMetadataEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeStringValuedMap(v.getValues());
            },
            in -> new DefaultTestKeyValueDataMetadataEvent(
                in.readLong(),
                in.readObject(InternalTestMetadataDescriptor.class),
                in.readStringValuedMap()
            )
        );
        register(DefaultTestFileAttachmentMetadataEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeFile(v.getFile());
                out.writeNullableString(v.getMediaType());
            },
            in -> new DefaultTestFileAttachmentMetadataEvent(
                in.readLong(),
                in.readObject(InternalTestMetadataDescriptor.class),
                in.readFile(),
                in.readNullableString()
            )
        );
        register(DefaultStatusEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeLong(v.getProgress());
                out.writeLong(v.getTotal());
                out.writeNullableString(v.getUnits());
            },
            in -> new DefaultStatusEvent(
                in.readLong(),
                in.readObject(DefaultFileDownloadDescriptor.class),
                in.readLong(),
                in.readLong(),
                in.readNullableString()
            )
        );
        register(DefaultProblemEvent.class,
            (out, v) -> {
                out.writeLong(v.getEventTime());
                out.writeObject(v.getDescriptor());
                out.writeObject(v.getDetails());
            },
            in -> new DefaultProblemEvent(
                in.readLong(),
                in.readObject(InternalProblemDescriptor.class),
                in.readObject(InternalProblemDetailsVersion2.class)
            )
        );
    }

    private static void registerDescriptors() {
        register(DefaultOperationDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
            },
            in -> new DefaultOperationDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId()
            )
        );
        register(DefaultRootOperationDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
            },
            in -> new DefaultRootOperationDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId()
            )
        );
        register(DefaultBuildPhaseDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
                out.writeNullableString(v.getBuildPhase());
                out.writeInt(v.getBuildItemsCount());
            },
            in -> new DefaultBuildPhaseDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId(),
                in.readNullableString(),
                in.readInt()
            )
        );
        register(DefaultFileDownloadDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
                out.writeUri(v.getUri());
            },
            in -> new DefaultFileDownloadDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId(),
                in.readUri()
            )
        );
        register(DefaultProjectConfigurationDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
                out.writeFile(v.getRootDir());
                out.writeNullableString(v.getProjectPath());
            },
            in -> new DefaultProjectConfigurationDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readOperationId(),
                in.readFile(),
                in.readNullableString()
            )
        );
        register(DefaultTransformDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
                out.writeNullableString(v.getTransformerName());
                out.writeNullableString(v.getSubjectName());
                out.writeSet(v.getDependencies());
            },
            in -> new DefaultTransformDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readSet(InternalOperationDescriptor.class)
            )
        );
        register(DefaultWorkItemDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getClassName());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
            },
            in -> new DefaultWorkItemDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId()
            )
        );
        register(DefaultTaskDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getTaskPath());
                out.writeNullableString(v.getDisplayName());
                out.writeOperationId(v.getParentId());
                out.writeSet(v.getDependencies());
                out.writeObject(v.getOriginPlugin());
            },
            in -> new DefaultTaskDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId(),
                in.readSet(InternalOperationDescriptor.class),
                in.readNullableObject(InternalPluginIdentifier.class)
            )
        );
        register(DefaultTestDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeNullableString(v.getTestDisplayName());
                out.writeNullableString(v.getTestKind());
                out.writeNullableString(v.getSuiteName());
                out.writeNullableString(v.getClassName());
                out.writeNullableString(v.getMethodName());
                out.writeOperationId(v.getParentId());
                out.writeNullableString(v.getTaskPath());
                out.writeObject(v.getSource());
            },
            in -> new DefaultTestDescriptor(
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readOperationId(),
                in.readNullableString(),
                in.readNullableObject(InternalTestSource.class)
            )
        );
        register(DefaultTestOutputDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeOperationId(v.getParentId());
            },
            in -> new DefaultTestOutputDescriptor(
                in.readOperationId(),
                in.readOperationId()
            )
        );
        register(DefaultTestMetadataDescriptor.class,
            (out, v) -> {
                out.writeOperationId(v.getId());
                out.writeOperationId(v.getParentId());
            },
            in -> new DefaultTestMetadataDescriptor(
                in.readOperationId(),
                in.readOperationId()
            )
        );
        register(DefaultProblemDescriptor.class,
            (out, v) -> {
                out.writeOperationId((OperationIdentifier) v.getId());
                out.writeOperationId((OperationIdentifier) v.getParentId());
            },
            in -> new DefaultProblemDescriptor(
                in.readOperationId(),
                in.readOperationId()
            )
        );
    }

    private static void registerResults() {
        register(DefaultSuccessResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
            },
            in -> new DefaultSuccessResult(
                in.readLong(),
                in.readLong()
            )
        );
        register(DefaultFailureResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeList(v.getFailures());
            },
            in -> new DefaultFailureResult(
                in.readLong(),
                in.readLong(),
                in.readList(InternalFailure.class)
            )
        );
        register(DefaultFileDownloadSuccessResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeLong(v.getBytesDownloaded());
            },
            in -> new DefaultFileDownloadSuccessResult(
                in.readLong(),
                in.readLong(),
                in.readLong()
            )
        );
        register(DefaultFileDownloadFailureResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeList(v.getFailures());
                out.writeLong(v.getBytesDownloaded());
            },
            in -> new DefaultFileDownloadFailureResult(
                in.readLong(),
                in.readLong(),
                in.readList(InternalFailure.class),
                in.readLong()
            )
        );
        register(NotFoundFileDownloadSuccessResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
            },
            in -> new NotFoundFileDownloadSuccessResult(
                in.readLong(),
                in.readLong()
            )
        );
        register(DefaultTaskSuccessResult.class,
            (out, v) -> {
                writeTaskSuccess(out, v);
            },
            BuildEventPayloadSerializer::readTaskSuccess
        );
        register(DefaultJavaCompileTaskSuccessResult.class,
            (out, v) -> {
                writeTaskSuccess(out, v);
                out.writeList(v.getAnnotationProcessorResults());
            },
            in -> new DefaultJavaCompileTaskSuccessResult(
                readTaskSuccess(in),
                in.readList(InternalAnnotationProcessorResult.class)
            )
        );
        register(DefaultTaskFailureResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeList(v.getFailures());
                out.writeBoolean(v.isIncremental());
                out.writeStringList(v.getExecutionReasons());
            },
            in -> new DefaultTaskFailureResult(
                in.readLong(),
                in.readLong(),
                in.readList(InternalFailure.class),
                in.readBoolean(),
                in.readStringList()
            )
        );
        register(DefaultTaskSkippedResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeNullableString(v.getSkipMessage());
                out.writeBoolean(v.isIncremental());
            },
            in -> new DefaultTaskSkippedResult(
                in.readLong(),
                in.readLong(),
                in.readNullableString(),
                in.readBoolean()
            )
        );
        register(DefaultTestSuccessResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
            },
            in -> new DefaultTestSuccessResult(
                in.readLong(),
                in.readLong()
            )
        );
        register(DefaultTestSkippedResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
            },
            in -> new DefaultTestSkippedResult(
                in.readLong(),
                in.readLong()
            )
        );
        register(DefaultTestFailureResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeList(v.getFailures());
            },
            in -> new DefaultTestFailureResult(
                in.readLong(),
                in.readLong(),
                in.readList(InternalFailure.class)
            )
        );
        register(DefaultTestOutputResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeInt(v.getDestination());
                out.writeNullableString(v.getMessage());
            },
            in -> new DefaultTestOutputResult(
                in.readLong(),
                in.readLong(),
                in.readInt(),
                in.readNullableString()
            )
        );
        register(DefaultProjectConfigurationSuccessResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeList(v.getPluginApplicationResults());
            },
            in -> new DefaultProjectConfigurationSuccessResult(
                in.readLong(),
                in.readLong(),
                in.readList(InternalPluginApplicationResult.class)
            )
        );
        register(DefaultProjectConfigurationFailureResult.class,
            (out, v) -> {
                out.writeLong(v.getStartTime());
                out.writeLong(v.getEndTime());
                out.writeList(v.getFailures());
                out.writeList(v.getPluginApplicationResults());
            },
            in -> new DefaultProjectConfigurationFailureResult(
                in.readLong(),
                in.readLong(),
                in.readList(InternalFailure.class),
                in.readList(InternalPluginApplicationResult.class)
            )
        );
        register(DefaultAnnotationProcessorResult.class,
            (out, v) -> {
                out.writeNullableString(v.getClassName());
                out.writeNullableString(v.getType());
                out.writeDuration(v.getDuration());
            },
            in -> new DefaultAnnotationProcessorResult(
                in.readNullableString(),
                in.readNullableString(),
                in.readDuration()
            )
        );
    }

    private static void writeTaskSuccess(Writer out, DefaultTaskSuccessResult v) throws Exception {
        out.writeLong(v.getStartTime());
        out.writeLong(v.getEndTime());
        out.writeBoolean(v.isUpToDate());
        out.writeBoolean(v.isFromCache());
        out.writeNullableString(v.getOutcomeDescription());
        out.writeBoolean(v.isIncremental());
        out.writeStringList(v.getExecutionReasons());
    }

    private static DefaultTaskSuccessResult readTaskSuccess(Reader in) throws Exception {
        return new DefaultTaskSuccessResult(
            in.readLong(),
            in.readLong(),
            in.readBoolean(),
            in.readBoolean(),
            in.readNullableString(),
            in.readBoolean(),
            in.readStringList()
        );
    }

    private static void registerFailures() {
        register(DefaultFailure.class,
            (out, v) -> {
                out.writeNullableString(v.getMessage());
                out.writeNullableString(v.getFullDescription());
                out.writeNullableString(v.getOwnDescription());
                out.writeList(v.getCauses());
                out.writeList(v.getProblems());
            },
            in -> new DefaultFailure(
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString(),
                in.readList(InternalFailure.class),
                in.readList(InternalBasicProblemDetailsVersion3.class)
            )
        );
        register(DefaultTestAssertionFailure.class,
            (out, v) -> {
                writeTestFailure(out, v);
                out.writeNullableString(v.getExpected());
                out.writeNullableString(v.getActual());
            },
            in -> {
                String message = in.readNullableString();
                String description = in.readNullableString();
                List<InternalFailure> causes = in.readList(InternalFailure.class);
                String className = in.readNullableString();
                String stacktrace = in.readNullableString();
                return new DefaultTestAssertionFailure(
                    message,
                    description,
                    causes,
                    in.readNullableString(),
                    in.readNullableString(),
                    className,
                    stacktrace
                );
            }
        );
        register(DefaultFileComparisonTestAssertionFailure.class,
            (out, v) -> {
                writeTestFailure(out, v);
                out.writeNullableString(v.getExpected());
                out.writeNullableString(v.getActual());
                out.writeNullableBinary(v.getExpectedContent());
                out.writeNullableBinary(v.getActualContent());
            },
            in -> {
                String message = in.readNullableString();
                String description = in.readNullableString();
                List<InternalFailure> causes = in.readList(InternalFailure.class);
                String className = in.readNullableString();
                String stacktrace = in.readNullableString();
                return new DefaultFileComparisonTestAssertionFailure(
                    message,
                    description,
                    causes,
                    in.readNullableString(),
                    in.readNullableString(),
                    className,
                    stacktrace,
                    in.readNullableBinary(),
                    in.readNullableBinary()
                );
            }
        );
        register(DefaultTestFrameworkFailure.class,
            BuildEventPayloadSerializer::writeTestFailure,
            in -> new DefaultTestFrameworkFailure(
                in.readNullableString(),
                in.readNullableString(),
                in.readList(InternalFailure.class),
                in.readNullableString(),
                in.readNullableString()
            )
        );
    }

    private static void writeTestFailure(Writer out, AbstractTestFailure v) throws Exception {
        out.writeNullableString(v.getMessage());
        out.writeNullableString(v.getDescription());
        out.writeList(v.getCauses());
        out.writeNullableString(v.getClassName());
        out.writeNullableString(v.getStacktrace());
    }

    private static void registerProblems() {
        register(DefaultProblemDetails.class,
            (out, v) -> {
                out.writeObject(v.getDefinition());
                out.writeObject(v.getDetails());
                out.writeObject(v.getContextualLabel());
                out.writeList(v.getOriginLocations());
                out.writeList(v.getContextualLocations());
                out.writeList(v.getSolutions());
                out.writeObject(v.getAdditionalData());
                out.writeObject(v.getFailure());
            },
            in -> new DefaultProblemDetails(
                in.readObject(InternalProblemDefinition.class),
                in.readNullableObject(InternalDetails.class),
                in.readNullableObject(InternalContextualLabel.class),
                in.readList(InternalLocation.class),
                in.readList(InternalLocation.class),
                in.readList(InternalSolution.class),
                in.readNullableObject(InternalAdditionalData.class),
                in.readNullableObject(InternalFailure.class)
            )
        );
        register(DefaultProblemsSummariesDetails.class,
            (out, v) -> out.writeList(v.getProblemIdCounts()),
            in -> new DefaultProblemsSummariesDetails(in.readList(InternalProblemSummary.class))
        );
        register(DefaultProblemSummary.class,
            (out, v) -> {
                out.writeObject(v.getProblemId());
                out.writeNullableInt(v.getCount());
            },
            in -> new DefaultProblemSummary(
                in.readObject(InternalProblemId.class),
                in.readNullableInt()
            )
        );
        register(DefaultProblemDefinition.class,
            (out, v) -> {
                out.writeObject(v.getId());
                out.writeObject(v.getSeverity());
                out.writeObject(v.getDocumentationLink());
            },
            in -> new DefaultProblemDefinition(
                in.readObject(InternalProblemId.class),
                in.readObject(InternalSeverity.class),
                in.readNullableObject(InternalDocumentationLink.class)
            )
        );
        register(DefaultProblemId.class,
            (out, v) -> {
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeObject(v.getGroup());
            },
            in -> new DefaultProblemId(
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableObject(InternalProblemGroup.class)
            )
        );
        register(DefaultProblemGroup.class,
            (out, v) -> {
                out.writeNullableString(v.getName());
                out.writeNullableString(v.getDisplayName());
                out.writeObject(v.getParent());
            },
            in -> new DefaultProblemGroup(
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableObject(InternalProblemGroup.class)
            )
        );
        register(DefaultSeverity.class,
            (out, v) -> out.writeInt(v.getSeverity()),
            in -> new DefaultSeverity(in.readInt())
        );
        register(DefaultDocumentationLink.class,
            (out, v) -> out.writeNullableString(v.getUrl()),
            in -> new DefaultDocumentationLink(in.readNullableString())
        );
        register(DefaultDetails.class,
            (out, v) -> out.writeNullableString(v.getDetails()),
            in -> new DefaultDetails(in.readNullableString())
        );
        register(DefaultContextualLabel.class,
            (out, v) -> out.writeNullableString(v.getContextualLabel()),
            in -> new DefaultContextualLabel(in.readNullableString())
        );
        register(DefaultLabel.class,
            (out, v) -> out.writeNullableString(v.getLabel()),
            in -> new DefaultLabel(in.readNullableString())
        );
        register(DefaultSolution.class,
            (out, v) -> out.writeNullableString(v.getSolution()),
            in -> new DefaultSolution(in.readNullableString())
        );
        register(DefaultFileLocation.class,
            (out, v) -> out.writeNullableString(v.getPath()),
            in -> new DefaultFileLocation(in.readNullableString())
        );
        register(DefaultLineInFileLocation.class,
            (out, v) -> {
                out.writeNullableString(v.getPath());
                out.writeInt(v.getLine());
                out.writeInt(v.getColumn());
                out.writeInt(v.getLength());
            },
            in -> new DefaultLineInFileLocation(
                in.readNullableString(),
                in.readInt(),
                in.readInt(),
                in.readInt()
            )
        );
        register(DefaultOffsetInFileLocation.class,
            (out, v) -> {
                out.writeNullableString(v.getPath());
                out.writeInt(v.getOffset());
                out.writeInt(v.getLength());
            },
            in -> new DefaultOffsetInFileLocation(
                in.readNullableString(),
                in.readInt(),
                in.readInt()
            )
        );
        register(DefaultPluginIdLocation.class,
            (out, v) -> out.writeNullableString(v.getPluginId()),
            in -> new DefaultPluginIdLocation(in.readNullableString())
        );
        register(DefaultTaskPathLocation.class,
            (out, v) -> out.writeNullableString(v.getBuildTreePath()),
            in -> new DefaultTaskPathLocation(in.readNullableString())
        );
        register(DefaultInternalAdditionalData.class,
            (out, v) -> out.writeStringValuedMap(v.getAsMap()),
            in -> new DefaultInternalAdditionalData(in.readStringValuedMap())
        );
        register(DefaultInternalPayloadSerializedAdditionalData.class,
            (out, v) -> {
                out.writeNullableBinary(v.getBytesForIsolatadObject());
                out.writePayload((SerializedPayload) v.getSerializedType());
            },
            in -> new DefaultInternalPayloadSerializedAdditionalData(
                in.readNullableBinary(),
                in.readPayload()
            )
        );
    }

    private static void registerPlugins() {
        register(DefaultBinaryPluginIdentifier.class,
            (out, v) -> {
                out.writeNullableString(v.getDisplayName());
                out.writeNullableString(v.getClassName());
                out.writeNullableString(v.getPluginId());
            },
            in -> new DefaultBinaryPluginIdentifier(
                in.readNullableString(),
                in.readNullableString(),
                in.readNullableString()
            )
        );
        register(DefaultScriptPluginIdentifier.class,
            (out, v) -> {
                out.writeNullableString(v.getDisplayName());
                out.writeUri(v.getUri());
            },
            in -> new DefaultScriptPluginIdentifier(
                in.readNullableString(),
                in.readUri()
            )
        );
        register(DefaultPluginApplicationResult.class,
            (out, v) -> {
                out.writeObject(v.getPlugin());
                out.writeDuration(v.getTotalConfigurationTime());
            },
            in -> new DefaultPluginApplicationResult(
                in.readObject(InternalPluginIdentifier.class),
                in.readDuration()
            )
        );
    }

    private static void registerTestSources() {
        register(DefaultClassSource.class,
            (out, v) -> out.writeNullableString(v.getClassName()),
            in -> new DefaultClassSource(in.readNullableString())
        );
        register(DefaultMethodSource.class,
            (out, v) -> {
                out.writeNullableString(v.getClassName());
                out.writeNullableString(v.getMethodName());
            },
            in -> new DefaultMethodSource(
                in.readNullableString(),
                in.readNullableString()
            )
        );
        register(DefaultFileSource.class,
            (out, v) -> {
                out.writeFile(v.getFile());
                out.writeObject(v.getPosition());
            },
            in -> new DefaultFileSource(
                in.readFile(),
                in.readNullableObject(InternalFilePosition.class)
            )
        );
        register(DefaultDirectorySource.class,
            (out, v) -> out.writeFile(v.getFile()),
            in -> new DefaultDirectorySource(in.readFile())
        );
        register(DefaultClasspathResourceSource.class,
            (out, v) -> {
                out.writeNullableString(v.getClasspathResourceName());
                out.writeObject(v.getPosition());
            },
            in -> new DefaultClasspathResourceSource(
                in.readNullableString(),
                in.readNullableObject(InternalFilePosition.class)
            )
        );
        register(DefaultNoSource.class,
            (out, v) -> {
            },
            in -> (DefaultNoSource) DefaultNoSource.getInstance()
        );
        register(DefaultOtherSource.class,
            (out, v) -> {
            },
            in -> (DefaultOtherSource) DefaultOtherSource.getInstance()
        );
        register(DefaultFilePosition.class,
            (out, v) -> {
                out.writeInt(v.getLine());
                out.writeNullableInt(v.getColumn());
            },
            in -> new DefaultFilePosition(
                in.readInt(),
                in.readNullableInt()
            )
        );
    }

    private static void registerActionResults() {
        register(PhasedBuildActionResult.class,
            (out, v) -> {
                out.writePayload(v.result);
                out.writeNullableString(v.phase.name());
            },
            in -> new PhasedBuildActionResult(
                in.readPayload(),
                PhasedActionResult.Phase.valueOf(in.readNullableString())
            )
        );
        register(StreamedValue.class,
            (out, v) -> out.writePayload(v.getSerializedModel()),
            in -> new StreamedValue(in.readPayload())
        );
    }

    private static <T> void register(Class<T> type, WriteAction<T> write, ReadAction<T> read) {
        Codec<T> codec = new Codec<T>(FIRST_TYPE_TAG + Codecs.BY_TAG.size(), write, read);
        if (Codecs.BY_TYPE.put(type, codec) != null) {
            throw new IllegalStateException("Duplicate codec for " + type.getName());
        }
        Codecs.BY_TAG.add(codec);
    }

    private interface WriteAction<T> {
        void write(Writer out, T value) throws Exception;
    }

    private interface ReadAction<T> {
        T read(Reader in) throws Exception;
    }

    private static final class Codec<T> {
        final int tag;
        final WriteAction<T> write;
        final ReadAction<T> read;

        Codec(int tag, WriteAction<T> write, ReadAction<T> read) {
            this.tag = tag;
            this.write = write;
            this.read = read;
        }
    }

    private static final class Writer {
        private final Encoder encoder;
        private final SerializedPayloadSerializer payloadSerializer;
        private final IdentityHashMap<Object, Integer> written = new IdentityHashMap<Object, Integer>();

        Writer(Encoder encoder, SerializedPayloadSerializer payloadSerializer) {
            this.encoder = encoder;
            this.payloadSerializer = payloadSerializer;
        }

        void writeObject(@Nullable Object value) throws Exception {
            if (value == null) {
                encoder.writeSmallInt(NULL);
                return;
            }
            Integer index = written.get(value);
            if (index != null) {
                encoder.writeSmallInt(BACK_REFERENCE);
                encoder.writeSmallInt(index);
                return;
            }
            @SuppressWarnings("unchecked")
            Codec<Object> codec = (Codec<Object>) Codecs.BY_TYPE.get(value.getClass());
            if (codec == null) {
                throw new IllegalArgumentException("Cannot serialize a build event payload of type " + value.getClass().getName());
            }
            encoder.writeSmallInt(codec.tag);
            codec.write.write(this, value);
            // Registered after the body, matching the reader, which can only register an object once it is constructed
            written.put(value, written.size());
        }

        void writeList(@Nullable Collection<?> values) throws Exception {
            if (values == null) {
                encoder.writeBoolean(false);
                return;
            }
            encoder.writeBoolean(true);
            encoder.writeSmallInt(values.size());
            for (Object value : values) {
                writeObject(value);
            }
        }

        void writeSet(@Nullable Set<?> values) throws Exception {
            writeList(values);
        }

        void writeStringList(@Nullable List<String> values) throws Exception {
            if (values == null) {
                encoder.writeBoolean(false);
                return;
            }
            encoder.writeBoolean(true);
            encoder.writeSmallInt(values.size());
            for (String value : values) {
                encoder.writeNullableString(value);
            }
        }

        void writeStringValuedMap(Map<String, ?> values) throws Exception {
            encoder.writeSmallInt(values.size());
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value != null && !(value instanceof String)) {
                    throw new IllegalArgumentException("Cannot serialize a build event value of type " + value.getClass().getName() + " for key '" + entry.getKey() + "'");
                }
                encoder.writeString(entry.getKey());
                encoder.writeNullableString((String) value);
            }
        }

        void writeOperationId(@Nullable OperationIdentifier id) throws Exception {
            encoder.writeBoolean(id != null);
            if (id != null) {
                encoder.writeLong(id.getId());
            }
        }

        void writeFile(@Nullable File file) throws Exception {
            encoder.writeNullableString(file == null ? null : file.getPath());
        }

        void writeUri(@Nullable URI uri) throws Exception {
            encoder.writeNullableString(uri == null ? null : uri.toString());
        }

        void writeDuration(@Nullable Duration duration) throws Exception {
            encoder.writeBoolean(duration != null);
            if (duration != null) {
                encoder.writeLong(duration.getSeconds());
                encoder.writeInt(duration.getNano());
            }
        }

        void writePayload(@Nullable SerializedPayload payload) throws Exception {
            encoder.writeBoolean(payload != null);
            if (payload != null) {
                payloadSerializer.write(encoder, payload);
            }
        }

        void writeNullableInt(@Nullable Integer value) throws Exception {
            encoder.writeBoolean(value != null);
            if (value != null) {
                encoder.writeInt(value);
            }
        }

        void writeNullableBinary(byte @Nullable [] value) throws Exception {
            encoder.writeBoolean(value != null);
            if (value != null) {
                encoder.writeBinary(value);
            }
        }

        void writeNullableString(@Nullable String value) throws Exception {
            encoder.writeNullableString(value);
        }

        void writeLong(long value) throws Exception {
            encoder.writeLong(value);
        }

        void writeInt(int value) throws Exception {
            encoder.writeInt(value);
        }

        void writeBoolean(boolean value) throws Exception {
            encoder.writeBoolean(value);
        }
    }

    private static final class Reader {
        private final Decoder decoder;
        private final SerializedPayloadSerializer payloadSerializer;
        private final List<Object> read = new ArrayList<Object>();

        Reader(Decoder decoder, SerializedPayloadSerializer payloadSerializer) {
            this.decoder = decoder;
            this.payloadSerializer = payloadSerializer;
        }

        <T> T readObject(Class<T> expectedType) throws Exception {
            T value = readNullableObject(expectedType);
            if (value == null) {
                throw new IllegalArgumentException("Unexpected null where " + expectedType.getName() + " was expected");
            }
            return value;
        }

        <T> @Nullable T readNullableObject(Class<T> expectedType) throws Exception {
            int tag = decoder.readSmallInt();
            Object value;
            if (tag == NULL) {
                return null;
            } else if (tag == BACK_REFERENCE) {
                int index = decoder.readSmallInt();
                if (index < 0 || index >= read.size()) {
                    throw new IllegalArgumentException("Unexpected back reference " + index);
                }
                value = read.get(index);
            } else {
                int codecIndex = tag - FIRST_TYPE_TAG;
                if (codecIndex < 0 || codecIndex >= Codecs.BY_TAG.size()) {
                    throw new IllegalArgumentException("Unexpected build event type tag " + tag);
                }
                value = Codecs.BY_TAG.get(codecIndex).read.read(this);
                read.add(value);
            }
            if (!expectedType.isInstance(value)) {
                throw new IllegalArgumentException("Unexpected " + value.getClass().getName() + " where " + expectedType.getName() + " was expected");
            }
            return expectedType.cast(value);
        }

        <T> @Nullable List<T> readList(Class<T> elementType) throws Exception {
            if (!decoder.readBoolean()) {
                return null;
            }
            int size = decoder.readSmallInt();
            List<T> values = new ArrayList<T>();
            for (int i = 0; i < size; i++) {
                values.add(readNullableObject(elementType));
            }
            return values;
        }

        <T> @Nullable Set<T> readSet(Class<T> elementType) throws Exception {
            List<T> values = readList(elementType);
            return values == null ? null : new LinkedHashSet<T>(values);
        }

        @Nullable List<String> readStringList() throws Exception {
            if (!decoder.readBoolean()) {
                return null;
            }
            int size = decoder.readSmallInt();
            List<String> values = new ArrayList<String>();
            for (int i = 0; i < size; i++) {
                values.add(decoder.readNullableString());
            }
            return values;
        }

        Map<String, Object> readStringValuedMap() throws Exception {
            int size = decoder.readSmallInt();
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            for (int i = 0; i < size; i++) {
                values.put(decoder.readString(), decoder.readNullableString());
            }
            return values;
        }

        @Nullable OperationIdentifier readOperationId() throws Exception {
            return decoder.readBoolean() ? new OperationIdentifier(decoder.readLong()) : null;
        }

        @Nullable File readFile() throws Exception {
            String path = decoder.readNullableString();
            return path == null ? null : new File(path);
        }

        @Nullable URI readUri() throws Exception {
            String uri = decoder.readNullableString();
            return uri == null ? null : new URI(uri);
        }

        @Nullable Duration readDuration() throws Exception {
            return decoder.readBoolean() ? Duration.ofSeconds(decoder.readLong(), decoder.readInt()) : null;
        }

        @Nullable SerializedPayload readPayload() throws Exception {
            return decoder.readBoolean() ? payloadSerializer.read(decoder) : null;
        }

        @Nullable Integer readNullableInt() throws Exception {
            return decoder.readBoolean() ? decoder.readInt() : null;
        }

        byte @Nullable [] readNullableBinary() throws Exception {
            return decoder.readBoolean() ? decoder.readBinary() : null;
        }

        @Nullable String readNullableString() throws Exception {
            return decoder.readNullableString();
        }

        long readLong() throws Exception {
            return decoder.readLong();
        }

        int readInt() throws Exception {
            return decoder.readInt();
        }

        boolean readBoolean() throws Exception {
            return decoder.readBoolean();
        }
    }
}
