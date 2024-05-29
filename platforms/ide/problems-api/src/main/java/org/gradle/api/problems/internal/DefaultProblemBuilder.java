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
import org.gradle.api.Action;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.SharedProblemGroup;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public  class DefaultProblemBuilder implements InternalProblemBuilder {

    private static List<Class<?>> supportedAdditionalDataTypes = ImmutableList.<Class<?>>of(
        GeneralDataSpec.class,
        DeprecationDataSpec.class,
        TypeValidationDataSpec.class
    );

    private ProblemId id;
    private String contextualLabel;
    private Severity severity;
    private final List<ProblemLocation> locations;
    private String details;
    private DocLink docLink;
    private List<String> solutions;
    private RuntimeException exception;
    private AdditionalData additionalData;
    private boolean collectLocation = false;

    public DefaultProblemBuilder(Problem problem) {
        this.id = problem.getDefinition().getId();
        this.contextualLabel = problem.getContextualLabel();
        this.solutions = new ArrayList<String>(problem.getSolutions());
        this.severity = problem.getDefinition().getSeverity();
        this.locations = new ArrayList<ProblemLocation>(problem.getLocations());
        this.details = problem.getDetails();
        this.docLink = problem.getDefinition().getDocumentationLink();
        this.exception = problem.getException();
        this.additionalData = problem.getAdditionalData();
    }

    public DefaultProblemBuilder() {
        this.locations = new ArrayList<ProblemLocation>();
        this.solutions = new ArrayList<String>();
        this.additionalData = null;
    }

    @Override
    public Problem build() {
        // id is mandatory
        if (id == null) {
            return invalidProblem("missing-id", "Problem id must be specified", null);
        } else if (id.getGroup() == null) {
            return invalidProblem("missing-parent", "Problem id must have a parent", null);
        }

        if (additionalData instanceof UnsupportedAdditionalDataSpec) {
            return invalidProblem("unsupported-additional-data", "Unsupported additional data type", "Unsupported additional data type: " + ((UnsupportedAdditionalDataSpec)additionalData).getType().getName() + ". Supported types are: " + supportedAdditionalDataTypes);
        }

        ProblemDefinition problemDefinition = new DefaultProblemDefinition(id, getSeverity(), docLink);
        return new DefaultProblem(problemDefinition, contextualLabel, solutions, locations, details, getExceptionForProblemInstantiation(), additionalData);
    }

    private Problem invalidProblem(String id, String displayName, String contextualLabel) {
        id(id, displayName, new DefaultProblemGroup(
            "problems-api",
            "Problems API")
        ).stackLocation();
        ProblemDefinition problemDefinition = new DefaultProblemDefinition(this.id, Severity.WARNING, null);
        return new DefaultProblem(problemDefinition, contextualLabel,
            ImmutableList.<String>of(),
            ImmutableList.<ProblemLocation>of(),
            null,
            getExceptionForProblemInstantiation(),
            null);
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
    public InternalProblemBuilder contextualLabel(String contextualLabel) {
        this.contextualLabel = contextualLabel;
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
    public InternalProblemBuilder documentedAt(@Nullable DocLink doc) {
        this.docLink = doc;
        return this;
    }

    @Override
    public InternalProblemBuilder id(String name, String displayName) {
        this.id = new DefaultProblemId(name, displayName, cloneGroup(SharedProblemGroup.generic()));
        return this;
    }

    @Override
    public InternalProblemBuilder id(String name, String displayName, ProblemGroup parent) {
        this.id = new DefaultProblemId(name, displayName, cloneGroup(parent));
        return this;
    }

    private static ProblemGroup cloneGroup(ProblemGroup original) {
        return new DefaultProblemGroup(original.getName(), original.getDisplayName(), original.getParent() == null ? null : cloneGroup(original.getParent()));
    }

    @Override
    public InternalProblemBuilder documentedAt(@Nullable String url) {
        this.docLink = url == null ? null : new DefaultDocLink(url);
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
    @SuppressWarnings("unchecked")
    public <U extends AdditionalDataSpec> InternalProblemBuilder additionalData(Class<? extends U> specType, Action<? super U> config) {
        if (!supportedAdditionalDataTypes.contains(specType)) {
            additionalData = new UnsupportedAdditionalDataSpec(specType);
        } else if (additionalData == null) {
            AdditionalDataBuilder<?> additionalDatabuilder = AdditionalDataBuilderFactory.builderFor(specType);
            config.execute((U) additionalDatabuilder);
            additionalData = additionalDatabuilder.build();
        } else {
            AdditionalDataBuilder<?> additionalDatabuilder = AdditionalDataBuilderFactory.builderFor(additionalData);
            config.execute((U) additionalDatabuilder);
            additionalData = additionalDatabuilder.build();
        }
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

    protected void addLocation(ProblemLocation location) {
        this.locations.add(location);
    }

    @SuppressWarnings("rawtypes")
    private static class UnsupportedAdditionalDataSpec implements AdditionalData {

        private final Class<?> type;

        UnsupportedAdditionalDataSpec(Class<?> type) {
            this.type = type;
        }

        public Class<?> getType() {
            return type;
        }
    }
}
