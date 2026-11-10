/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.LineInFileLocation;
import org.gradle.api.problems.OffsetInFileLocation;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails;
import org.gradle.api.problems.internal.DeprecationData;
import org.gradle.api.problems.internal.GeneralData;
import org.gradle.api.problems.internal.PluginIdLocation;
import org.gradle.api.problems.internal.ProblemGroupInternal;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.gradle.api.problems.internal.StackTraceLocation;
import org.gradle.api.problems.internal.TaskLocation;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.api.problems.internal.TypedAdditionalData;
import org.gradle.internal.build.event.types.DefaultContextualLabel;
import org.gradle.internal.build.event.types.DefaultDetails;
import org.gradle.internal.build.event.types.DefaultDocumentationLink;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultInternalAdditionalData;
import org.gradle.internal.build.event.types.DefaultInternalPayloadSerializedAdditionalData;
import org.gradle.internal.build.event.types.DefaultProblemDefinition;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.build.event.types.DefaultProblemGroup;
import org.gradle.internal.build.event.types.DefaultProblemId;
import org.gradle.internal.build.event.types.DefaultProblemSummary;
import org.gradle.internal.build.event.types.DefaultProblemsSummariesDetails;
import org.gradle.internal.build.event.types.DefaultSeverity;
import org.gradle.internal.build.event.types.DefaultSolution;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.InternalProblemEventVersion2;
import org.gradle.tooling.internal.protocol.InternalProblemGroup;
import org.gradle.tooling.internal.protocol.InternalProblemId;
import org.gradle.tooling.internal.protocol.InternalProblemSummary;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;
import org.gradle.tooling.internal.protocol.problem.InternalFrame;
import org.gradle.tooling.internal.protocol.problem.InternalPosition;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;
import org.gradle.tooling.internal.protocol.problem.InternalTrace;
import org.gradle.api.problems.internal.location.BuildFrame;
import org.gradle.api.problems.internal.location.CompiledCodePosition;
import org.gradle.api.problems.internal.location.FilePosition;
import org.gradle.api.problems.internal.location.Frame;
import org.gradle.api.problems.internal.location.Position;
import org.gradle.api.problems.internal.location.PluginApplicationFrame;
import org.gradle.api.problems.internal.location.ProjectFrame;
import org.gradle.api.problems.internal.location.TaskFrame;
import org.gradle.api.problems.internal.location.Trace;
import org.gradle.internal.build.event.types.DefaultBuildFrame;
import org.gradle.internal.build.event.types.DefaultProblemCompiledCodePosition;
import org.gradle.internal.build.event.types.DefaultProblemFilePosition;
import org.gradle.internal.build.event.types.DefaultFrame;
import org.gradle.internal.build.event.types.DefaultPluginApplicationFrame;
import org.gradle.internal.build.event.types.DefaultProjectFrame;
import org.gradle.internal.build.event.types.DefaultTaskFrame;
import org.gradle.internal.build.event.types.DefaultTrace;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toMap;

@NullMarked
public class ProblemsProgressEventUtils {

    private static final InternalSeverity ADVICE = new DefaultSeverity(0);
    private static final InternalSeverity WARNING = new DefaultSeverity(1);
    private static final InternalSeverity ERROR = new DefaultSeverity(2);

    public ProblemsProgressEventUtils() {
    }

    static InternalProblemEventVersion2 createProblemEvent(OperationIdentifier buildOperationId, DefaultProblemProgressDetails details, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        ProblemInternal problem = details.getProblem();
        return new DefaultProblemEvent(
            createDefaultProblemDescriptor(buildOperationId, operationIdentifierSupplier),
            createDefaultProblemDetails(problem)
        );
    }

    static InternalProblemEventVersion2 createProblemSummaryEvent(@Nullable OperationIdentifier buildOperationId, DefaultProblemsSummaryProgressDetails details, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        return createProblemSummaryEvent(buildOperationId, details.getProblemIdCounts(), operationIdentifierSupplier);
    }

    private static InternalProblemEventVersion2 createProblemSummaryEvent(OperationIdentifier buildOperationId, List<ProblemSummaryData> problemIdCounts, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        List<InternalProblemSummary> internalIdCounts = problemIdCounts.stream()
            .map(it -> new DefaultProblemSummary(toInternalId(it.getProblemId()), it.getCount()))
            .collect(toImmutableList());
        return new DefaultProblemEvent(
            createDefaultProblemDescriptor(buildOperationId, operationIdentifierSupplier),
            new DefaultProblemsSummariesDetails(internalIdCounts)
        );
    }

    @Nullable
    private static InternalFailure toInternalFailure(@Nullable Throwable ex) {
        if (ex == null) {
            return null;
        }
        return DefaultFailure.fromThrowable(ex);
    }

    private static InternalProblemDescriptor createDefaultProblemDescriptor(OperationIdentifier parentBuildOperationId, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        return new DefaultProblemDescriptor(
            operationIdentifierSupplier.get(),
            parentBuildOperationId);
    }

    static DefaultProblemDetails createDefaultProblemDetails(ProblemInternal problem) {
        return new DefaultProblemDetails(
            toInternalDefinition(problem.getDefinition()),
            toInternalDetails(problem.getDetails()),
            toInternalContextualLabel(problem.getContextualLabel()),
            toInternalLocations(problem.getOriginLocations()),
            toInternalLocations(problem.getContextualLocations()),
            toInternalSolutions(problem.getSolutions()),
            toInternalAdditionalData(problem),
            toInternalFailure(problem.getException()),
            toInternalDetectionTrace(problem.getDetectionTrace()),
            toInternalTraces(problem.getOriginTraces())
        );
    }

    private static List<InternalTrace> toInternalTraces(List<Trace> originTraces) {
        return originTraces.stream().map(ProblemsProgressEventUtils::toInternalTrace).collect(toImmutableList());
    }

    private static @Nullable InternalTrace toInternalDetectionTrace(@Nullable Trace trace) {
        return trace == null ? null : toInternalTrace(trace);
    }

    private static InternalTrace toInternalTrace(Trace trace) {
        return new DefaultTrace(trace.getFrames().stream()
            .map(ProblemsProgressEventUtils::toInternalFrame)
            .collect(toImmutableList()));
    }

    private static InternalFrame toInternalFrame(Frame frame) {
        String displayName = frame.getDisplayName();
        String locality = frame.getLocality().name();
        InternalPosition position = toInternalPosition(frame.getPosition());
        if (frame instanceof TaskFrame) {
            return new DefaultTaskFrame(displayName, locality, position, ((TaskFrame) frame).getBuildTreePath());
        }
        if (frame instanceof ProjectFrame) {
            return new DefaultProjectFrame(displayName, locality, position, ((ProjectFrame) frame).getBuildTreePath());
        }
        if (frame instanceof BuildFrame) {
            return new DefaultBuildFrame(displayName, locality, position, ((BuildFrame) frame).getBuildPath());
        }
        if (frame instanceof PluginApplicationFrame) {
            return new DefaultPluginApplicationFrame(displayName, locality, position, ((PluginApplicationFrame) frame).getPluginId());
        }
        // A frame kind without a typed protocol class crosses as a plain frame
        return new DefaultFrame(displayName, locality, position);
    }

    private static @Nullable InternalPosition toInternalPosition(@Nullable Position position) {
        if (position instanceof FilePosition) {
            FilePosition file = (FilePosition) position;
            return new DefaultProblemFilePosition(file.getPath(), file.getLine(), file.getColumn(), file.getOffset(), file.getLength());
        }
        if (position instanceof CompiledCodePosition) {
            CompiledCodePosition compiled = (CompiledCodePosition) position;
            return new DefaultProblemCompiledCodePosition(compiled.getFileName(), compiled.getClassName(), compiled.getModule(), compiled.getLine());
        }
        return null;
    }

    private static InternalProblemDefinition toInternalDefinition(ProblemDefinition definition) {
        return new DefaultProblemDefinition(
            toInternalId(definition.getId()),
            toInternalSeverity(definition.getSeverity()),
            toInternalDocumentationLink(definition.getDocumentationLink())
        );
    }

    // sends the display name of a legacy id; 9.x clients read it until Gradle 11, so Gradle 10 sends the name
    private static InternalProblemId toInternalId(ProblemId problemId) {
        return new DefaultProblemId(problemId.getName(), org.gradle.api.problems.internal.DefaultProblemId.displayNameOf(problemId), toInternalGroup(ProblemGroupInternal.of(problemId.getGroup())));
    }

    private static InternalProblemGroup toInternalGroup(ProblemGroupInternal group) {
        ProblemGroupInternal parent = group.getParentInternal();
        return new DefaultProblemGroup(group.getName(), group.getDisplayNameInternal(), parent == null ? null : toInternalGroup(parent));
    }

    private static @Nullable InternalContextualLabel toInternalContextualLabel(@Nullable String contextualLabel) {
        return contextualLabel == null ? null : new DefaultContextualLabel(contextualLabel);
    }

    private static @Nullable InternalDetails toInternalDetails(@Nullable String details) {
        return details == null ? null : new DefaultDetails(details);
    }

    private static InternalSeverity toInternalSeverity(Severity severity) {
        switch (severity) {
            case ADVICE:
                return ADVICE;
            case WARNING:
                return WARNING;
            case ERROR:
                return ERROR;
            default:
                throw new RuntimeException("No mapping defined for severity level " + severity);
        }
    }

    private static List<InternalLocation> toInternalLocations(List<ProblemLocation> locations) {
        return locations.stream()
            .filter(location -> !(location instanceof StackTraceLocation && ((StackTraceLocation) location).getFileLocation() == null))
            .map(location -> location instanceof StackTraceLocation
                ? ((StackTraceLocation) location).getFileLocation()
                : location)
            .map(location -> {
                if (location instanceof LineInFileLocation) {
                    LineInFileLocation fileLocation = (LineInFileLocation) location;
                    return new org.gradle.internal.build.event.types.DefaultLineInFileLocation(fileLocation.getPath(), fileLocation.getLine(), fileLocation.getColumn(), fileLocation.getLength());
                } else if (location instanceof OffsetInFileLocation) {
                    OffsetInFileLocation fileLocation = (OffsetInFileLocation) location;
                    return new org.gradle.internal.build.event.types.DefaultOffsetInFileLocation(fileLocation.getPath(), fileLocation.getOffset(), fileLocation.getLength());
                } else if (location instanceof FileLocation) { // generic class must be after the subclasses in the if-elseif chain.
                    FileLocation fileLocation = (FileLocation) location;
                    return new org.gradle.internal.build.event.types.DefaultFileLocation(fileLocation.getPath());
                } else if (location instanceof PluginIdLocation) {
                    PluginIdLocation pluginLocation = (PluginIdLocation) location;
                    return new org.gradle.internal.build.event.types.DefaultPluginIdLocation(pluginLocation.getPluginId());
                } else if (location instanceof TaskLocation) {
                    TaskLocation taskLocation = (TaskLocation) location;
                    return new org.gradle.internal.build.event.types.DefaultTaskPathLocation(taskLocation.getBuildTreePath());
                } else {
                    throw new RuntimeException("No mapping defined for " + location.getClass().getName());
                }
            }).collect(toImmutableList());
    }

    @Nullable
    private static InternalDocumentationLink toInternalDocumentationLink(@Nullable DocLink link) {
        return (link == null || link.getUrl() == null) ? null : new DefaultDocumentationLink(link.getUrl());
    }

    private static List<InternalSolution> toInternalSolutions(List<String> solutions) {
        return solutions.stream()
            .map(DefaultSolution::new)
            .collect(toImmutableList());
    }


    private static InternalAdditionalData toInternalAdditionalData(ProblemInternal problem) {
        Object additionalData = problem.getAdditionalData();
        if (additionalData instanceof DeprecationData) {
            // For now, we only expose deprecation data to the tooling API with generic additional data
            DeprecationData data = (DeprecationData) additionalData;
            return new DefaultInternalAdditionalData(ImmutableMap.of("type", data.getType().name()));
        } else if (additionalData instanceof TypeValidationData) {
            TypeValidationData data = (TypeValidationData) additionalData;
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            Optional.ofNullable(data.getPluginId()).ifPresent(pluginId -> builder.put("pluginId", pluginId));
            Optional.ofNullable(data.getPropertyName()).ifPresent(propertyName -> builder.put("propertyName", propertyName));
            Optional.ofNullable(data.getParentPropertyName()).ifPresent(parentPropertyName -> builder.put("parentPropertyName", parentPropertyName));
            Optional.ofNullable(data.getTypeName()).ifPresent(typeName -> builder.put("typeName", typeName));
            return new DefaultInternalAdditionalData(builder.build());
        } else if (additionalData instanceof GeneralData) {
            GeneralData data = (GeneralData) additionalData;
            return new DefaultInternalAdditionalData(
                data.getAsMap().entrySet().stream()
                    .filter(entry -> isSupportedType(entry.getValue()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        } else if (additionalData instanceof TypedAdditionalData) {
            TypedAdditionalData typedData = (TypedAdditionalData) additionalData;
            return new DefaultInternalPayloadSerializedAdditionalData(typedData.getBytesForIsolatedObject(), typedData.getSerializedType());
        } else {
            return new DefaultInternalAdditionalData(Collections.emptyMap());
        }
    }

    private static boolean isSupportedType(Object type) {
        return type instanceof String;
    }

}
