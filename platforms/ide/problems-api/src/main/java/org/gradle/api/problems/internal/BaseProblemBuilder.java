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

import org.gradle.api.Incubating;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderDefiningCategory;
import org.gradle.api.problems.ProblemBuilderDefiningDocumentation;
import org.gradle.api.problems.ProblemBuilderDefiningLabel;
import org.gradle.api.problems.ProblemBuilderDefiningLocation;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.locations.FileLocation;
import org.gradle.api.problems.locations.PluginIdLocation;
import org.gradle.api.problems.locations.ProblemLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for problems.
 *
 * @since 8.3
 */
@Incubating
public class BaseProblemBuilder implements ProblemBuilder,
    ProblemBuilderDefiningDocumentation,
    ProblemBuilderDefiningLocation,
    ProblemBuilderDefiningLabel,
    ProblemBuilderDefiningCategory {

    protected String label;
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

    @Override
    public ProblemBuilderDefiningDocumentation label(String label, Object... args) {
        this.label = String.format(label, args);
        return this;
    }

    @Override
    public BaseProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    public ProblemBuilderDefiningCategory location(String path, @Nullable Integer line) {
        location(path, line, null);
        return this;
    }

    public ProblemBuilderDefiningCategory location(String path, @Nullable Integer line, @Nullable Integer column) {
        this.locations.add(new FileLocation(path, line, column, 0));
        return this;
    }

    public ProblemBuilderDefiningCategory fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length) {
        this.locations.add(new FileLocation(path, line, column, length));
        return this;
    }

    @Override
    public ProblemBuilderDefiningCategory pluginLocation(String pluginId) {
        this.locations.add(new PluginIdLocation(pluginId));
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

    public BaseProblemBuilder details(String details) {
        this.description = details;
        return this;
    }

    public ProblemBuilderDefiningLocation documentedAt(DocLink doc) {
        this.documentationUrl = doc;
        return this;
    }

    @Override
    public ProblemBuilderDefiningLocation undocumented() {
        this.explicitlyUndocumented = true;
        return this;
    }

    @Override
    public ProblemBuilder category(String category, String... details){
        this.problemCategory = DefaultProblemCategory.category(category, details).toString();
        return this;
    }

    public BaseProblemBuilder solution(@Nullable String solution) {
        if (this.solution == null) {
            this.solution = new ArrayList<String>();
        }
        this.solution.add(solution);
        return this;
    }

    public BaseProblemBuilder additionalData(String key, String value) {
        this.additionalMetadata.put(key, value);
        return this;
    }

    @Override
    public BaseProblemBuilder withException(RuntimeException e) {
        this.exception = e;
        return this;
    }

    public Problem build() { // TODO this can be simplified
        return buildInternal(null);
    }

    @Nonnull
    private Problem buildInternal(@Nullable Severity severity) {
        if (!explicitlyUndocumented && documentationUrl == null) {
            throw new IllegalStateException("Problem is not documented: " + label);
        }
        return new DefaultProblem(
            label,
            getSeverity(severity),
            locations,
            documentationUrl,
            description,
            solution,
            exception == null && collectLocation ? new Exception() : exception, //TODO: don't create exception if already reported often
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

    RuntimeException getException() {
        return exception;
    }
}
