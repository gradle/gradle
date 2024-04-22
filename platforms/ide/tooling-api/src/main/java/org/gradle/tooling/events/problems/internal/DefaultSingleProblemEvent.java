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

package org.gradle.tooling.events.problems.internal;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.BaseProgressEvent;
import org.gradle.tooling.events.problems.AdditionalData;
import org.gradle.tooling.events.problems.Details;
import org.gradle.tooling.events.problems.DocumentationLink;
import org.gradle.tooling.events.problems.FailureContainer;
import org.gradle.tooling.events.problems.Label;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.ProblemCategory;
import org.gradle.tooling.events.problems.Severity;
import org.gradle.tooling.events.problems.SingleProblemEvent;
import org.gradle.tooling.events.problems.Solution;

import javax.annotation.Nullable;
import java.util.List;

@NonNullApi
public class DefaultSingleProblemEvent extends BaseProgressEvent implements SingleProblemEvent {
    private final ProblemCategory category;
    private final Severity severity;
    private final Label label;
    private final Details details;
    private final List<Location> locations;
    private final DocumentationLink documentationLink;
    private final List<Solution> solutions;
    private final AdditionalData additionalData;
    private final FailureContainer failure;

    public DefaultSingleProblemEvent(
        long eventTime,
        @Nullable OperationDescriptor problemDescriptor,
        ProblemCategory category,
        Severity severity,
        Label label,
        Details details,
        List<Location> locations,
        DocumentationLink documentationLink,
        List<Solution> solutions,
        AdditionalData additionalData,
        @Nullable FailureContainer failure) {
        super(eventTime, problemDescriptor == null ? "<null>" : problemDescriptor.getDisplayName(), problemDescriptor);
        this.category = category;
        this.severity = severity;
        this.label = label;
        this.details = details;
        this.locations = locations;
        this.documentationLink = documentationLink;
        this.solutions = solutions;
        this.additionalData = additionalData;
        this.failure = failure;
    }

    @Override
    public ProblemCategory getCategory() {
        return category;
    }

    @Override
    public Label getLabel() {
        return label;
    }

    @Override
    public Details getDetails() {
        return details;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public List<Location> getLocations() {
        return locations;
    }

    @Override
    public DocumentationLink getDocumentationLink() {
        return documentationLink;
    }

    @Override
    public List<Solution> getSolutions() {
        return solutions;
    }

    @Nullable
    @Override
    public FailureContainer getFailure() {
        return failure;
    }

    public AdditionalData getAdditionalData() {
        return additionalData;
    }
}
