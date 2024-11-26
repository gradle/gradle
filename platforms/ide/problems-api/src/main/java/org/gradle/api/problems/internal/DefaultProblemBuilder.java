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
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.AdditionalDataBuilder;
import org.gradle.api.problems.AdditionalDataBuilderFactory;
import org.gradle.api.problems.AdditionalDataSpec;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.SharedProblemGroup;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemStream;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DefaultProblemBuilder implements InternalProblemBuilder {
    @Nullable
    private ProblemStream problemStream;

    private ProblemId id;
    private String contextualLabel;
    private Severity severity;
    private final ImmutableList.Builder<ProblemLocation> locations = ImmutableList.builder();
    private String details;
    private DocLink docLink;
    private List<String> solutions;
    private Throwable exception;
    private AdditionalData additionalData;
    private boolean collectLocation = false;
    private final AdditionalDataBuilderFactory additionalDataBuilderFactory;

    public DefaultProblemBuilder(AdditionalDataBuilderFactory additionalDataBuilderFactory) {
        this.additionalDataBuilderFactory = additionalDataBuilderFactory;
        this.additionalData = null;
        this.solutions = new ArrayList<String>();
    }

    public DefaultProblemBuilder(@Nullable ProblemStream problemStream, AdditionalDataBuilderFactory additionalDataBuilderFactory) {
        this(additionalDataBuilderFactory);
        this.problemStream = problemStream;
    }

    public DefaultProblemBuilder(Problem problem, AdditionalDataBuilderFactory additionalDataBuilderFactory) {
        this(additionalDataBuilderFactory);
        this.id = problem.getDefinition().getId();
        this.contextualLabel = problem.getContextualLabel();
        this.solutions = new ArrayList<String>(problem.getSolutions());
        this.severity = problem.getDefinition().getSeverity();

        locations.addAll(problem.getOriginLocations());

        this.details = problem.getDetails();
        this.docLink = problem.getDefinition().getDocumentationLink();
        this.exception = problem.getException();
        this.additionalData = problem.getAdditionalData();
        this.problemStream = null;
    }

    @Override
    public Problem build() {
        // id is mandatory
        if (getId() == null) {
            return invalidProblem("missing-id", "Problem id must be specified", null);
        } else if (getId().getGroup() == null) {
            return invalidProblem("missing-parent", "Problem id must have a parent", null);
        }

        if (additionalData instanceof UnsupportedAdditionalDataSpec) {
            return invalidProblem("unsupported-additional-data", "Unsupported additional data type",
                "Unsupported additional data type: " + ((UnsupportedAdditionalDataSpec) additionalData).getType().getName() +
                    ". Supported types are: " + additionalDataBuilderFactory.getSupportedTypes());
        }

        Throwable exceptionForProblemInstantiation = getExceptionForProblemInstantiation();
        if (problemStream != null) {
            addLocationsFromProblemStream(this.locations, exceptionForProblemInstantiation);
        }

        ProblemDefinition problemDefinition = new DefaultProblemDefinition(getId(), getSeverity(), docLink);
        return new DefaultProblem(
            problemDefinition,
            contextualLabel,
            solutions,
            locations.build(),
            ImmutableList.<ProblemLocation>of(),
            details,
            exceptionForProblemInstantiation,
            additionalData
        );
    }

    private void addLocationsFromProblemStream(ImmutableList.Builder<ProblemLocation> locations, Throwable exceptionForProblemInstantiation) {
        assert problemStream != null;
        ProblemDiagnostics problemDiagnostics = problemStream.forCurrentCaller(exceptionForProblemInstantiation);
        Location loc = problemDiagnostics.getLocation();
        if (loc != null) {
            locations.add(getFileLocation(loc));
        }
        if (problemDiagnostics.getSource() != null && problemDiagnostics.getSource().getPluginId() != null) {
            locations.add(getDefaultPluginIdLocation(problemDiagnostics));
        }
    }

    private static DefaultPluginIdLocation getDefaultPluginIdLocation(ProblemDiagnostics problemDiagnostics) {
        assert problemDiagnostics.getSource() != null;
        return new DefaultPluginIdLocation(problemDiagnostics.getSource().getPluginId());
    }

    private static FileLocation getFileLocation(Location loc) {
        String path = loc.getSourceLongDisplayName().getDisplayName();
        int line = loc.getLineNumber();
        return DefaultLineInFileLocation.from(path, line);
    }

    private Problem invalidProblem(String id, String displayName, @Nullable String contextualLabel) {
        id(id, displayName, new DefaultProblemGroup(
            "problems-api",
            "Problems API")
        ).stackLocation();
        ProblemDefinition problemDefinition = new DefaultProblemDefinition(this.getId(), Severity.WARNING, null);
        Throwable exceptionForProblemInstantiation = getExceptionForProblemInstantiation();
        ImmutableList.Builder<ProblemLocation> problemLocations = ImmutableList.builder();
        addLocationsFromProblemStream(problemLocations, exceptionForProblemInstantiation);
        return new DefaultProblem(problemDefinition, contextualLabel,
            ImmutableList.<String>of(),
            problemLocations.build(),
            ImmutableList.<ProblemLocation>of(),
            null,
            exceptionForProblemInstantiation,
            null);
    }

    public Throwable getExceptionForProblemInstantiation() {
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
        if (additionalDataBuilderFactory.hasProviderForSpec(specType)) {
            AdditionalDataBuilder<?> additionalDataBuilder = additionalDataBuilderFactory.createAdditionalDataBuilder(specType, additionalData);
            config.execute((U) additionalDataBuilder);
            additionalData = additionalDataBuilder.build();
        } else {
            additionalData = new UnsupportedAdditionalDataSpec(specType);
        }
        return this;
    }

    @Override
    public InternalProblemBuilder withException(Throwable t) {
        this.exception = t;
        return this;
    }

    @Nullable
    Throwable getException() {
        return exception;
    }

    protected void addLocation(ProblemLocation location) {
        this.locations.add(location);
    }

    public ProblemId getId() {
        return id;
    }

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
