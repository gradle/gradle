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
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.UnboundBasicProblemBuilder;
import org.gradle.api.problems.locations.FileLocation;
import org.gradle.api.problems.locations.PluginIdLocation;
import org.gradle.api.problems.locations.ProblemLocation;
import org.gradle.api.problems.locations.TaskPathLocation;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultBasicProblemBuilder implements UnboundBasicProblemBuilder {

    private String label;
    private String problemCategory;
    private Severity severity;
    private List<ProblemLocation> locations;
    private String details;
    private DocLink docLink;
    private boolean explicitlyUndocumented;
    private List<String> solutions;
    private RuntimeException exception;
    private final Map<String, String> additionalMetadata;
    private boolean collectLocation = false;

    public DefaultBasicProblemBuilder(Problem problem) {
        this.label = problem.getLabel();
        this.problemCategory = problem.getProblemCategory().toString();
        this.severity = problem.getSeverity();
        this.locations = new ArrayList<ProblemLocation>(problem.getLocations());
        this.details = problem.getDetails();
        this.docLink = problem.getDocumentationLink();
        this.explicitlyUndocumented = problem.getDocumentationLink() == null;
        this.solutions = new ArrayList<String>(problem.getSolutions());
        this.exception = problem.getException();
        this.additionalMetadata = new HashMap<String, String>(problem.getAdditionalData());
    }

    public DefaultBasicProblemBuilder() {
        this.locations = new ArrayList<ProblemLocation>();
        this.additionalMetadata = new HashMap<String, String>();
    }

    @Override
    public Problem build() {
        return new DefaultProblem(
            getLabel(),
            getSeverity(getSeverity()),
            getLocations(),
            getDocLink(),
            getDetails(),
            getSolutions(),
            getExceptionForProblemInstantiation(), // TODO: don't create exception if already reported often
            getProblemCategory(),
            getAdditionalMetadata());
    }

    public RuntimeException getExceptionForProblemInstantiation() {
        return getException() == null && isCollectLocation() ? new RuntimeException() : getException();
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

    public UnboundBasicProblemBuilder label(String label, Object... args) {
        this.label = String.format(label, args);
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder taskPathLocation(Path taskPath) {
        this.getLocations().add(new TaskPathLocation(taskPath));
        return this;
    }

    public UnboundBasicProblemBuilder location(String path, @javax.annotation.Nullable Integer line) {
        location(path, line, null);
        return this;
    }

    public UnboundBasicProblemBuilder location(String path, @javax.annotation.Nullable Integer line, @javax.annotation.Nullable Integer column) {
        this.getLocations().add(new FileLocation(path, line, column, 0));
        return this;
    }

    public UnboundBasicProblemBuilder fileLocation(String path, @javax.annotation.Nullable Integer line, @javax.annotation.Nullable Integer column, @javax.annotation.Nullable Integer length) {
        this.getLocations().add(new FileLocation(path, line, column, length));
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder pluginLocation(String pluginId) {
        this.getLocations().add(new PluginIdLocation(pluginId));
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder stackLocation() {
        this.collectLocation = true;
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder noLocation() {
        return this;
    }

    public UnboundBasicProblemBuilder details(String details) {
        this.details = details;
        return this;
    }

    public UnboundBasicProblemBuilder documentedAt(DocLink doc) {
        this.explicitlyUndocumented = false;
        this.docLink = doc;
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder undocumented() {
        this.explicitlyUndocumented = true;
        this.docLink = null;
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder category(String category, String... details){
        this.problemCategory = DefaultProblemCategory.category(category, details).toString();
        return this;
    }

    public UnboundBasicProblemBuilder solution(@Nullable String solution) {
        if (this.getSolutions() == null) {
            this.solutions = new ArrayList<String>();
        }
        this.getSolutions().add(solution);
        return this;
    }

    public UnboundBasicProblemBuilder additionalData(String key, String value) {
        this.getAdditionalMetadata().put(key, value);
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder withException(RuntimeException e) {
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

    protected String getProblemCategory() {
        return problemCategory;
    }

    protected List<ProblemLocation> getLocations() {
        return locations;
    }

    protected String getDetails() {
        return details;
    }

    protected DocLink getDocLink() {
        return docLink;
    }

    protected boolean isExplicitlyUndocumented() {
        return explicitlyUndocumented;
    }

    protected List<String> getSolutions() {
        return solutions;
    }

    protected Map<String, String> getAdditionalMetadata() {
        return additionalMetadata;
    }

    protected boolean isCollectLocation() {
        return collectLocation;
    }
}
