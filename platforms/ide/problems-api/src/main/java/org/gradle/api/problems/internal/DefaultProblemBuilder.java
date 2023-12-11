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
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemCategory;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultProblemBuilder implements InternalProblemBuilder {

    private final String namespace;
    private String label;
    private ProblemCategory problemCategory;
    private Severity severity;
    private List<ProblemLocation> locations;
    private String details;
    private DocLink docLink;
    private List<String> solutions;
    private RuntimeException exception;
    private final Map<String, Object> additionalData;
    private boolean collectLocation = false;

    public DefaultProblemBuilder(Problem problem) {
        this.label = problem.getLabel();
        this.problemCategory = problem.getProblemCategory();
        this.severity = problem.getSeverity();
        this.locations = new ArrayList<ProblemLocation>(problem.getLocations());
        this.details = problem.getDetails();
        this.docLink = problem.getDocumentationLink();
        this.solutions = new ArrayList<String>(problem.getSolutions());
        this.exception = problem.getException();
        this.additionalData = new HashMap<String, Object>(problem.getAdditionalData());
        this.namespace = problem.getProblemCategory().getNamespace();
    }

    public DefaultProblemBuilder(String namespace) {
        this.namespace = namespace;
        this.locations = new ArrayList<ProblemLocation>();
        this.additionalData = new HashMap<String, Object>();
    }

    @Override
    public Problem build() {
        if (label == null) {
            throw new IllegalStateException("Label must be specified");
        } else if (problemCategory == null) {
            throw new IllegalStateException("Category must be specified");
        }
        return new DefaultProblem(
            label,
            getSeverity(getSeverity()),
            locations,
            docLink,
            details,
            solutions,
            getExceptionForProblemInstantiation(), // TODO: don't create exception if already reported often
            problemCategory,
            additionalData
        );
    }

    public RuntimeException getExceptionForProblemInstantiation() {
        return getException() == null && collectLocation ? new RuntimeException() : getException();
    }

    protected Severity getSeverity(@Nullable Severity severity) {
        if (severity != null) {
            return severity;
        }
        return getSeverity();
    }

    protected Severity getSeverity() {
        if (this.severity == null) {
            return Severity.WARNING;
        }
        return this.severity;
    }

    @Override
    public InternalProblemBuilder label(String label, Object... args) {
        this.label = String.format(label, args);
        return this;
    }

    @Override
    public InternalProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    public InternalProblemBuilder taskPathLocation(String buildTreePath) {
        this.addLocation(new DefaultTaskPathLocation(buildTreePath));
        return this;
    }

    public InternalProblemBuilder location(String path, @javax.annotation.Nullable Integer line) {
        location(path, line, null);
        return this;
    }

    public InternalProblemBuilder location(String path, @javax.annotation.Nullable Integer line, @javax.annotation.Nullable Integer column) {
        this.addLocation(new DefaultFileLocation(path, line, column, 0));
        return this;
    }

    @Override
    public InternalProblemBuilder fileLocation(String path, @javax.annotation.Nullable Integer line, @javax.annotation.Nullable Integer column, @javax.annotation.Nullable Integer length) {
        this.addLocation(new DefaultFileLocation(path, line, column, length));
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
        this.problemCategory = DefaultProblemCategory.create(namespace, category, details);
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
        validateAdditionalDataValueType(value);
        this.additionalData.put(key, value);
        return this;
    }

    private void validateAdditionalDataValueType(Object value) {
        if (!(value instanceof String)) {
            throw new RuntimeException("ProblemBuilder.additionalData() supports values of type String, but " + value.getClass().getName() + " as given.");
        }
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
