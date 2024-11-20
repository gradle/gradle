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
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.AdditionalData;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails;
import org.gradle.api.problems.internal.DeprecationData;
import org.gradle.api.problems.internal.DocLink;
import org.gradle.api.problems.internal.FileLocation;
import org.gradle.api.problems.internal.GeneralData;
import org.gradle.api.problems.internal.LineInFileLocation;
import org.gradle.api.problems.internal.OffsetInFileLocation;
import org.gradle.api.problems.internal.PluginIdLocation;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemDefinition;
import org.gradle.api.problems.internal.ProblemLocation;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.gradle.api.problems.internal.TaskPathLocation;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.internal.build.event.types.DefaultAdditionalData;
import org.gradle.internal.build.event.types.DefaultContextualLabel;
import org.gradle.internal.build.event.types.DefaultDetails;
import org.gradle.internal.build.event.types.DefaultDocumentationLink;
import org.gradle.internal.build.event.types.DefaultFailure;
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
import org.gradle.tooling.internal.protocol.problem.InternalSolution;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toMap;

@NonNullApi
public class ProblemsProgressEventUtils {

    private static final InternalSeverity ADVICE = new DefaultSeverity(0);
    private static final InternalSeverity WARNING = new DefaultSeverity(1);
    private static final InternalSeverity ERROR = new DefaultSeverity(2);

    private ProblemsProgressEventUtils() {
    }

    static InternalProblemEventVersion2 createProblemEvent(OperationIdentifier buildOperationId, DefaultProblemProgressDetails details, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        return createProblemEvent(buildOperationId, details.getProblem(), operationIdentifierSupplier);
    }

    static InternalProblemEventVersion2 createProblemSummaryEvent(@Nullable OperationIdentifier buildOperationId, DefaultProblemsSummaryProgressDetails details, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        return createProblemSummaryEvent(buildOperationId, details.getProblemIdCounts(), operationIdentifierSupplier);
    }

    private static InternalProblemEventVersion2 createProblemEvent(OperationIdentifier buildOperationId, Problem problem, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        return new DefaultProblemEvent(
            createDefaultProblemDescriptor(buildOperationId, operationIdentifierSupplier),
            createDefaultProblemDetails(problem)
        );
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

    static DefaultProblemDetails createDefaultProblemDetails(Problem problem) {
        return new DefaultProblemDetails(
            toInternalDefinition(problem.getDefinition()),
            toInternalDetails(problem.getDetails()),
            toInternalContextualLabel(problem.getContextualLabel()),
            toInternalLocations(problem.getOriginLocations()),
            toInternalLocations(problem.getContextualLocations()),
            toInternalSolutions(problem.getSolutions()),
            toInternalAdditionalData(problem.getAdditionalData()),
            toInternalFailure(problem.getException())
        );
    }

    private static InternalProblemDefinition toInternalDefinition(ProblemDefinition definition) {
        return new DefaultProblemDefinition(
            toInternalId(definition.getId()),
            toInternalSeverity(definition.getSeverity()),
            toInternalDocumentationLink(definition.getDocumentationLink())
        );
    }

    private static InternalProblemId toInternalId(ProblemId problemId) {
        return new DefaultProblemId(problemId.getName(), problemId.getDisplayName(), toInternalGroup(problemId.getGroup()));
    }

    private static InternalProblemGroup toInternalGroup(ProblemGroup group) {
        return new DefaultProblemGroup(group.getName(), group.getDisplayName(), group.getParent() == null ? null : toInternalGroup(group.getParent()));
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
        return locations.stream().map(location -> {
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
            } else if (location instanceof TaskPathLocation) {
                TaskPathLocation taskLocation = (TaskPathLocation) location;
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


    @SuppressWarnings("unchecked")
    private static InternalAdditionalData toInternalAdditionalData(@Nullable AdditionalData additionalData) {
        if (additionalData instanceof DeprecationData) {
            // For now, we only expose deprecation data to the tooling API with generic additional data
            DeprecationData data = (DeprecationData) additionalData;
            return new DefaultAdditionalData(ImmutableMap.of("type", data.getType().name()));
        } else if (additionalData instanceof TypeValidationData) {
            TypeValidationData data = (TypeValidationData) additionalData;
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
            Optional.ofNullable(data.getPluginId()).ifPresent(pluginId -> builder.put("pluginId", pluginId));
            Optional.ofNullable(data.getPropertyName()).ifPresent(propertyName -> builder.put("propertyName", propertyName));
            Optional.ofNullable(data.getParentPropertyName()).ifPresent(parentPropertyName -> builder.put("parentPropertyName", parentPropertyName));
            Optional.ofNullable(data.getTypeName()).ifPresent(typeName -> builder.put("typeName", typeName));
            return new DefaultAdditionalData(builder.build());
        } else if (additionalData instanceof GeneralData) {
            GeneralData data = (GeneralData) additionalData;
            return new DefaultAdditionalData(
                data.getAsMap().entrySet().stream()
                    .filter(entry -> isSupportedType(entry.getValue()))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        } else {
            return new DefaultAdditionalData(Collections.emptyMap());
        }
    }

    private static boolean isSupportedType(Object type) {
        return type instanceof String;
    }
}
