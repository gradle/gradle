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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultBasicProblemBuilder implements UnboundBasicProblemBuilder {

    protected String label; // TODO make them private
    protected String problemCategory;
    protected Severity severity;
    protected List<ProblemLocation> locations = new ArrayList<ProblemLocation>();
    protected String description;
    protected DocLink documentationUrl;
    protected boolean explicitlyUndocumented = false;
    protected List<String> solution;
    protected RuntimeException exception;
    protected final Map<String, String> additionalMetadata = new HashMap<String, String>();
    protected boolean collectLocation = false;

    public DefaultBasicProblemBuilder(Problem problem) {
        this.label = problem.getLabel();
        this.problemCategory = problem.getProblemCategory().getCategory();
        this.severity = problem.getSeverity();
        this.locations.addAll(problem.getLocations());
        this.description = problem.getDetails(); // TODO change field name
        this.documentationUrl = problem.getDocumentationLink(); // TODO change field name
        this.explicitlyUndocumented = problem.getDocumentationLink() == null;
        this.solution = new ArrayList<String>(problem.getSolutions()); // TODO rename to solutions
        this.exception = (RuntimeException) problem.getException(); // TODO ensure this is valid
    }

    @Override
    public Problem build() {
        return new DefaultProblem(
            label,
            getSeverity(severity),
            locations,
            documentationUrl,
            description,
            solution,
            exception == null && collectLocation ? new Exception() : exception, // TODO: don't create exception if already reported often
            problemCategory,
            additionalMetadata);
    }

    @Nonnull
    private Severity getSeverity(@Nullable Severity severity) {
        if (severity != null) {
            return severity;
        }
        return getSeverity();
    }

    private Severity getSeverity() {
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
    public UnboundBasicProblemBuilder location(ProblemLocation location) {
        this.locations.add(location);
        return this;
    }

    public UnboundBasicProblemBuilder location(String path, @javax.annotation.Nullable Integer line) {
        location(path, line, null);
        return this;
    }

    public UnboundBasicProblemBuilder location(String path, @javax.annotation.Nullable Integer line, @javax.annotation.Nullable Integer column) {
        this.locations.add(new FileLocation(path, line, column, 0));
        return this;
    }

    public UnboundBasicProblemBuilder fileLocation(String path, @javax.annotation.Nullable Integer line, @javax.annotation.Nullable Integer column, @javax.annotation.Nullable Integer length) {
        this.locations.add(new FileLocation(path, line, column, length));
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder pluginLocation(String pluginId) {
        this.locations.add(new PluginIdLocation(pluginId));
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
        this.description = details;
        return this;
    }

    public UnboundBasicProblemBuilder documentedAt(DocLink doc) {
        this.explicitlyUndocumented = false;
        this.documentationUrl = doc;
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder undocumented() {
        this.explicitlyUndocumented = true;
        this.documentationUrl = null;
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder category(String category, String... details){
        this.problemCategory = DefaultProblemCategory.category(category, details).toString();
        return this;
    }

    public UnboundBasicProblemBuilder solution(@Nullable String solution) {
        if (this.solution == null) {
            this.solution = new ArrayList<String>();
        }
        this.solution.add(solution);
        return this;
    }

    public UnboundBasicProblemBuilder additionalData(String key, String value) {
        this.additionalMetadata.put(key, value);
        return this;
    }

    @Override
    public UnboundBasicProblemBuilder withException(RuntimeException e) {
        this.exception = e;
        return this;
    }
}
