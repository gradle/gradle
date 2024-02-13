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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.DefaultProblemProgressDetails;
import org.gradle.api.problems.internal.DocLink;
import org.gradle.api.problems.internal.FileLocation;
import org.gradle.api.problems.internal.LineInFileLocation;
import org.gradle.api.problems.internal.OffsetInFileLocation;
import org.gradle.api.problems.internal.PluginIdLocation;
import org.gradle.api.problems.internal.ProblemReport;
import org.gradle.api.problems.internal.ProblemCategory;
import org.gradle.api.problems.internal.ProblemLocation;
import org.gradle.api.problems.internal.TaskPathLocation;
import org.gradle.internal.build.event.types.DefaultAdditionalData;
import org.gradle.internal.build.event.types.DefaultDetails;
import org.gradle.internal.build.event.types.DefaultDocumentationLink;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultLabel;
import org.gradle.internal.build.event.types.DefaultProblemCategory;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.build.event.types.DefaultSeverity;
import org.gradle.internal.build.event.types.DefaultSolution;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemEvent;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink;
import org.gradle.tooling.internal.protocol.problem.InternalLabel;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalProblemCategory;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toMap;

@NonNullApi
public class ProblemsProgressEventConsumer extends ClientForwardingBuildOperationListener {

    private static final InternalSeverity ADVICE = new DefaultSeverity(0);
    private static final InternalSeverity WARNING = new DefaultSeverity(1);
    private static final InternalSeverity ERROR = new DefaultSeverity(2);

    private final Supplier<OperationIdentifier> operationIdentifierSupplier;
    private final AggregatingProblemConsumer aggregator;

    ProblemsProgressEventConsumer(ProgressEventConsumer progressEventConsumer, Supplier<OperationIdentifier> operationIdentifierSupplier, AggregatingProblemConsumer aggregator) {
        super(progressEventConsumer);
        this.operationIdentifierSupplier = operationIdentifierSupplier;
        this.aggregator = aggregator;
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        createProblemEvent(buildOperationId, details)
            .ifPresent(aggregator::emit);
    }

    private Optional<InternalProblemEvent> createProblemEvent(OperationIdentifier buildOperationId, @Nullable Object details) {
        if (details instanceof DefaultProblemProgressDetails) {
            ProblemReport problem = ((DefaultProblemProgressDetails) details).getProblem();
            return of(createProblemEvent(buildOperationId, problem));
        }
        return empty();
    }

    private DefaultProblemEvent createProblemEvent(OperationIdentifier buildOperationId, ProblemReport problem) {
        return new DefaultProblemEvent(
            cerateDefaultProblemDescriptor(buildOperationId),
            new DefaultProblemDetails(
                toInternalCategory(problem.getDefinition().getCategory()),
                toInternalLabel(problem.getDefinition().getLabel()),
                toInternalDetails(problem.getContext().getDetails()),
                toInternalSeverity(problem.getDefinition().getSeverity()),
                toInternalLocations(problem.getContext().getLocations()),
                toInternalDocumentationLink(problem.getDefinition().getDocumentationLink()),
                toInternalSolutions(problem.getDefinition().getSolutions()),
                toInternalAdditionalData(problem.getContext().getAdditionalData()),
                toInternalFailure(problem.getContext().getException())
            )
        );
    }

    @Nullable
    private static InternalFailure toInternalFailure(@Nullable RuntimeException ex) {
        if (ex == null) {
            return null;
        }
        return DefaultFailure.fromThrowable(ex);
    }

    private DefaultProblemDescriptor cerateDefaultProblemDescriptor(OperationIdentifier parentBuildOperationId) {
        return new DefaultProblemDescriptor(
            operationIdentifierSupplier.get(),
            parentBuildOperationId);
    }

    private static InternalProblemCategory toInternalCategory(ProblemCategory category) {
        return new DefaultProblemCategory(category.getNamespace(), category.getCategory(), category.getSubcategories());
    }

    private static InternalLabel toInternalLabel(String label) {
        return new DefaultLabel(label);
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

    private static InternalAdditionalData toInternalAdditionalData(Map<String, Object> additionalData) {
        return new DefaultAdditionalData(
            additionalData.entrySet().stream()
                .filter(entry -> isSupportedType(entry.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
    }

    private static boolean isSupportedType(Object type) {
        return type instanceof String;
    }
}
