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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderDefiningCategory;
import org.gradle.api.problems.ProblemBuilderDefiningDocumentation;
import org.gradle.api.problems.ProblemBuilderDefiningLocation;
import org.gradle.api.problems.ProblemCategory;
import org.gradle.api.problems.ProblemCloneBuilder;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.locations.FileLocation;
import org.gradle.api.problems.locations.PluginIdLocation;
import org.gradle.api.problems.locations.ProblemLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultProblemCloneBuilder implements ProblemCloneBuilder {
    private ProblemCategory category;
    private String label;
    private String description;
    private Severity severity;
    private DocLink docLink;
    private ArrayList<String> solutions;
    private ArrayList<ProblemLocation> locations;
    private Map<String, String> additionalData;
    private Throwable exception;
    private boolean collectLocation;

    public DefaultProblemCloneBuilder(DefaultProblem defaultProblem) {
        this.category = defaultProblem.getProblemCategory();
        this.label = defaultProblem.getLabel();
        this.description = defaultProblem.getDetails();
        this.severity = defaultProblem.getSeverity();
        this.docLink = defaultProblem.getDocumentationLink();
        this.solutions = new ArrayList<String>(defaultProblem.getSolutions());
        this.locations = new ArrayList<ProblemLocation>(defaultProblem.getLocations());
        this.additionalData = new HashMap<String, String>(defaultProblem.getAdditionalData());
        this.exception = defaultProblem.getException();
    }

    @Override
    public ReportableProblem build() {
        return new DefaultReportableProblem(
            this.label,
            this.severity,
            this.locations,
            this.docLink,
            this.description,
            this.solutions,
            this.exception,
            this.category.getCategory(),
            this.additionalData,
            null
        );
    }

    @Override
    public ProblemBuilder details(String details) {
        this.description = details;
        return this;
    }

    @Override
    public ProblemBuilder solution(String solution) {
        this.getSolutions().add(solution);
        return this;
    }

    @Override
    public ProblemBuilder additionalData(String key, String value) {
        this.getAdditionalData().put(key, value);
        return this;
    }

    @Override
    public ProblemBuilder withException(RuntimeException e) {
        this.exception = e;
        return this;
    }

    @Override
    public ProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    @Override
    public ProblemBuilder category(String category, String... details) {
        this.category = DefaultProblemCategory.category(category, details);
        return this;
    }

    @Override
    public ProblemBuilderDefiningLocation documentedAt(DocLink doc) {
        this.docLink = doc;
        return this;
    }

    @Override
    public ProblemBuilderDefiningLocation undocumented() {
        return this;
    }

    @Override
    public ProblemBuilderDefiningDocumentation label(String label, Object... args) {
        this.label = String.format(label, args);
        return this;
    }

    @Override
    public ProblemBuilderDefiningCategory fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length) {
        this.getLocations().add(new FileLocation(path, line, column, length));
        return this;
    }

    @Override
    public ProblemBuilderDefiningCategory pluginLocation(String pluginId) {
        this.getLocations().add(new PluginIdLocation(pluginId));
        return this;
    }

    @Override
    public ProblemBuilderDefiningCategory stackLocation() {
        this.collectLocation = true;
        return this;
    }

    @Override
    public ProblemBuilderDefiningCategory noLocation() {
        return this;
    }

    public List<String> getSolutions() {
        return solutions;
    }

    public List<ProblemLocation> getLocations() {
        return locations;
    }

    public Map<String, String> getAdditionalData() {
        return additionalData;
    }
}
