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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultProblemBuilder implements InternalProblemBuilder {

    private final String namespace;
    private String label;
    private String contextualLabel;
    private List<String> contextualSolutions;
    private ProblemCategory category;
    private Severity severity;
    private final List<ProblemLocation> locations;
    private String details;
    private DocLink docLink;
    private List<String> solutions;
    private RuntimeException exception;
    private final Map<String, Object> additionalData;
    private boolean collectLocation = false;

    public DefaultProblemBuilder(Problem problem) {
        this.label = problem.getDefinition().getLabel();
        this.contextualLabel = problem.getContextualLabel();
        this.contextualSolutions = new ArrayList<String>(problem.getContextualSolutions());
        this.category = problem.getDefinition().getCategory();
        this.severity = problem.getDefinition().getSeverity();
        this.locations = new ArrayList<ProblemLocation>(problem.getLocations());
        this.details = problem.getDetails();
        this.docLink = problem.getDefinition().getDocumentationLink();
        this.solutions = new ArrayList<String>(problem.getDefinition().getSolutions());
        this.exception = problem.getException();
        this.additionalData = new HashMap<String, Object>(problem.getAdditionalData());
        this.namespace = problem.getDefinition().getCategory().getNamespace();
    }

    public DefaultProblemBuilder(String namespace) {
        this.namespace = namespace;
        this.locations = new ArrayList<ProblemLocation>();
        this.contextualSolutions = new ArrayList<String>();
        this.additionalData = new HashMap<String, Object>();
    }

    @Override
    public Problem build() {
        // Label is mandatory
        if (label == null) {
            return missingLabelProblem();
        }

        // Description is mandatory
        if (category == null) {
            return missingCategoryProblem();
        }

        // We need to explicitly manage serializing the data from the daemon to the tooling API client, hence the restriction.
        for (Object value : additionalData.values()) {
            if (!(value instanceof String)) {
                return invalidProblem("ProblemBuilder.additionalData() supports values of type String, but " + value.getClass().getName() + " as given.", "invalid-additional-data");
            }
        }

        ProblemDefinition problemDefinition = new DefaultProblemDefinition(label, getSeverity(), docLink, solutions, category);
        return new DefaultProblemReport(problemDefinition, contextualLabel, contextualSolutions, locations, details, getExceptionForProblemInstantiation(), additionalData);
    }

    private Problem missingLabelProblem() {
        return invalidProblem("problem label must be specified", "missing-label");
    }

    private Problem missingCategoryProblem() {
        return invalidProblem("problem category must be specified", "missing-category");
    }

    private Problem invalidProblem(String label, String subcategory) {
        category("validation", "problems-api", subcategory).stackLocation();
        ProblemDefinition problemDefinition = new DefaultProblemDefinition(label, Severity.WARNING, null, Collections.<String>emptyList(), category);
        return new DefaultProblemReport(problemDefinition, null,
            ImmutableList.<String>of(),
            ImmutableList.<ProblemLocation>of(),
            null,
            getExceptionForProblemInstantiation(),
            ImmutableMap.<String, Object>of());
    }

    public RuntimeException getExceptionForProblemInstantiation() {
        return getException() == null && collectLocation ? new RuntimeException() : getException();
    }

    protected Severity getSeverity() {
        if (this.severity == null) {
            return Severity.WARNING;
        }
        return this.severity;
    }

    @Override
    public InternalProblemBuilder label(String label) {
        this.label = label;
        return this;
    }

    @Override
    public InternalProblemBuilder contextualLabel(String contextualLabel) {
        this.contextualLabel = contextualLabel;
        return this;
    }

    @Override
    public InternalProblemSpec contextualSolution(String contextualSolution) {
        this.contextualSolutions.add(contextualSolution);
        return this;
    }

    @Override
    public InternalProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    @Override
    public InternalProblemBuilder taskPathLocation(String buildTreePath) {
        this.addLocation(new DefaultTaskPathLocation(buildTreePath));
        return this;
    }

    @Override
    public InternalProblemBuilder fileLocation(String path) {
        this.addLocation(DefaultFileLocation.from(path));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line) {
        this.addLocation(DefaultLineInFileLocation.from(path, line));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column) {
        this.addLocation(DefaultLineInFileLocation.from(path, line, column));
        return this;
    }

    @Override
    public InternalProblemBuilder offsetInFileLocation(String path, int offset, int length) {
        this.addLocation(DefaultOffsetInFileLocation.from(path, offset, length));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column, int length) {
        this.addLocation(DefaultLineInFileLocation.from(path, line, column, length));
        return this;
    }

    @Override
    public InternalProblemBuilder pluginLocation(String pluginId) {
        this.addLocation(new DefaultPluginIdLocation(pluginId));
        return this;
    }

    @Override
    public InternalProblemBuilder stackLocation() {
        this.collectLocation = true;
        return this;
    }

    @Override
    public InternalProblemBuilder details(String details) {
        this.details = details;
        return this;
    }

    @Override
    public InternalProblemBuilder documentedAt(DocLink doc) {
        this.docLink = doc;
        return this;
    }

    @Override
    public InternalProblemBuilder documentedAt(String url) {
        this.docLink = new DefaultDocLink(url);
        return this;
    }

    @Override
    public InternalProblemBuilder category(String category, String... details) {
        this.category = DefaultProblemCategory.create(namespace, category, details);
        return this;
    }

    @Override
    public InternalProblemBuilder solution(@Nullable String solution) {
        if (this.solutions == null) {
            this.solutions = new ArrayList<String>();
        }
        this.solutions.add(solution);
        return this;
    }

    @Override
    public InternalProblemBuilder additionalData(String key, Object value) {
        this.additionalData.put(key, value);
        return this;
    }

    @Override
    public InternalProblemBuilder withException(RuntimeException e) {
        this.exception = e;
        return this;
    }

    @Nullable
    RuntimeException getException() {
        return exception;
    }

    protected String getLabel() {
        return label;
    }

    protected void addLocation(ProblemLocation location) {
        this.locations.add(location);
    }
}
