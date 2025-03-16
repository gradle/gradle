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
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.Severity;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultProblemBuilder implements InternalProblemBuilder {
    private final ProblemsInfrastructure problemsInfrastructure;

    private ProblemId id;
    private String contextualLabel;
    private Severity severity;
    private final List<ProblemLocation> locations = new ArrayList<ProblemLocation>();
    private final List<ProblemLocation> contextLocations = new ArrayList<ProblemLocation>();
    private String details;
    private DocLink docLink;
    private List<String> solutions;
    private Throwable exception;
    private AdditionalData additionalData;
    private boolean collectStackLocation = false;
    private ProblemDiagnostics diagnostics;

    public DefaultProblemBuilder(
        ProblemsInfrastructure infrastructure
    ) {
        this.problemsInfrastructure = infrastructure;
        this.additionalData = null;
        this.solutions = new ArrayList<String>();
    }

    public DefaultProblemBuilder(
        InternalProblem problem,
        ProblemsInfrastructure infrastructure
    ) {
        this(infrastructure);
        this.id = problem.getDefinition().getId();
        this.contextualLabel = problem.getContextualLabel();
        this.solutions = new ArrayList<String>(problem.getSolutions());
        this.severity = problem.getDefinition().getSeverity();
        this.locations.addAll(problem.getOriginLocations());
        this.contextLocations.addAll(problem.getContextualLocations());
        this.details = problem.getDetails();
        this.docLink = problem.getDefinition().getDocumentationLink();
        this.exception = problem.getException();
        this.additionalData = problem.getAdditionalData();
    }

    @Override
    public InternalProblem build() {
        // id is mandatory
        if (getId() == null) {
            return invalidProblem("missing-id", "Problem id must be specified", null);
        } else if (getId().getGroup() == null) {
            return invalidProblem("missing-parent", "Problem id must have a parent", null);
        }

        if (additionalData instanceof UnsupportedAdditionalDataSpec) {
            return invalidProblem("unsupported-additional-data", "Unsupported additional data type",
                "Unsupported additional data type: " + ((UnsupportedAdditionalDataSpec) additionalData).getType().getName() +
                    ". Supported types are: " + problemsInfrastructure.getAdditionalDataBuilderFactory().getSupportedTypes());
        }

        ProblemDiagnostics diagnostics = determineDiagnostics();
        if (diagnostics != null) {
            addLocationsFromDiagnostics(collectStackLocation ? this.locations : this.contextLocations, diagnostics);
        }

        ProblemDefinition problemDefinition = new DefaultProblemDefinition(getId(), getSeverity(), docLink);
        return new DefaultProblem(
            problemDefinition,
            contextualLabel,
            solutions,
            locations,
            contextLocations,
            details,
            exception,
            additionalData
        );
    }

    @Nullable
    private ProblemDiagnostics determineDiagnostics() {
        if (diagnostics != null) {
            return diagnostics;
        }
        return problemsInfrastructure.getProblemStream() != null
            ? problemsInfrastructure.getProblemStream().forCurrentCaller(exceptionForStackLocation())
            : null;
    }

    private void addLocationsFromDiagnostics(List<ProblemLocation> locations, ProblemDiagnostics diagnostics) {
        Location loc = diagnostics.getLocation();
        FileLocation fileLocation = loc == null ? null : getFileLocation(loc);
        if (fileLocation != null) {
            locations.remove(fileLocation);
        }
        if (collectStackLocation || fileLocation != null) {
            locations.add(new DefaultStackTraceLocation(fileLocation, diagnostics.getStack()));
        }

        PluginIdLocation pluginIdLocation = getDefaultPluginIdLocation(diagnostics);
        if (pluginIdLocation != null) {
            locations.add(pluginIdLocation);
        }
    }

    @Nullable
    private static PluginIdLocation getDefaultPluginIdLocation(ProblemDiagnostics problemDiagnostics) {
        UserCodeSource source = problemDiagnostics.getSource();
        if (source == null) {
            return null;
        }
        String pluginId = source.getPluginId();
        if (pluginId == null) {
            return null;
        }
        return new DefaultPluginIdLocation(pluginId);
    }

    private static FileLocation getFileLocation(Location loc) {
        String path = loc.getFilePath();
        int line = loc.getLineNumber();
        return DefaultLineInFileLocation.from(path, line);
    }

    private InternalProblem invalidProblem(String id, String displayName, @Nullable String contextualLabel) {
        id(id, displayName, ProblemGroup.create(
            "problems-api",
            "Problems API")
        ).stackLocation();
        ProblemDefinition problemDefinition = new DefaultProblemDefinition(this.getId(), Severity.WARNING, null);
        List<ProblemLocation> problemLocations = new ArrayList<ProblemLocation>();
        ProblemDiagnostics diagnostics = determineDiagnostics();
        if (diagnostics != null) {
            addLocationsFromDiagnostics(problemLocations, diagnostics);
        }
        return new DefaultProblem(problemDefinition,
            contextualLabel,
            ImmutableList.<String>of(),
            problemLocations,
            ImmutableList.<ProblemLocation>of(),
            null,
            null,
            null
        );
    }

    public Throwable exceptionForStackLocation() {
        return getException() == null && collectStackLocation ? new RuntimeException() : getException();
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
    public ProblemsInfrastructure getInfrastructure() {
        return problemsInfrastructure;
    }

    @Override
    public InternalProblemBuilder taskLocation(String buildTreePath) {
        this.contextLocations.add(new DefaultTaskLocation(buildTreePath));
        return this;
    }

    @Override
    public InternalProblemBuilder fileLocation(String path) {
        addFileLocation(DefaultFileLocation.from(path));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line) {
        return addFileLocation(DefaultLineInFileLocation.from(path, line));
    }

    @NonNull
    private DefaultProblemBuilder addFileLocation(FileLocation from) {
        return addFileLocationTo(this.locations, from);
    }

    @NonNull
    private DefaultProblemBuilder addFileLocationTo(List<ProblemLocation> problemLocations, FileLocation from) {
        if (problemLocations.contains(from)) {
            return this;
        }
        problemLocations.add(from);
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column) {
        addFileLocation(DefaultLineInFileLocation.from(path, line, column));
        return this;
    }

    @Override
    public InternalProblemBuilder offsetInFileLocation(String path, int offset, int length) {
        addFileLocation(DefaultOffsetInFileLocation.from(path, offset, length));
        return this;
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column, int length) {
        addFileLocation(DefaultLineInFileLocation.from(path, line, column, length));
        return this;
    }

    @Override
    public InternalProblemBuilder stackLocation() {
        this.collectStackLocation = true;
        return this;
    }

    @Override
    public InternalProblemSpec diagnostics(ProblemDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
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
    public InternalProblemBuilder id(ProblemId problemId) {
        if (problemId instanceof DefaultProblemId) {
            this.id = problemId;
        } else {
            this.id = cloneId(problemId);
        }
        return this;
    }

    @Override
    public InternalProblemBuilder id(String name, String displayName, ProblemGroup parent) {
        this.id = ProblemId.create(name, displayName, cloneGroup(parent));
        return this;
    }

    private static ProblemId cloneId(ProblemId original) {
        return ProblemId.create(original.getName(), original.getDisplayName(), cloneGroup(original.getGroup()));
    }

    private static ProblemGroup cloneGroup(ProblemGroup original) {
        return ProblemGroup.create(original.getName(), original.getDisplayName(), original.getParent() == null ? null : cloneGroup(original.getParent()));
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
    public <U extends AdditionalDataSpec> InternalProblemBuilder additionalDataInternal(Class<? extends U> specType, Action<? super U> config) {
        if (problemsInfrastructure.getAdditionalDataBuilderFactory().hasProviderForSpec(specType)) {
            AdditionalDataBuilder<? extends AdditionalData> additionalDataBuilder = problemsInfrastructure.getAdditionalDataBuilderFactory().createAdditionalDataBuilder(specType, additionalData);
            config.execute((U) additionalDataBuilder);
            additionalData = additionalDataBuilder.build();
        } else {
            additionalData = new UnsupportedAdditionalDataSpec(specType);
        }
        return this;
    }

    @Override
    public <T extends AdditionalData> InternalProblemBuilder additionalData(Class<T> type, Action<? super T> config) {
        AdditionalData additionalDataInstance = createAdditionalData(type, config);
        Isolatable<AdditionalData> isolated = problemsInfrastructure.getIsolatableFactory().isolate(additionalDataInstance);

        SerializedPayload serializedBaseClass = problemsInfrastructure.getPayloadSerializer().serialize(type);
        byte[] serialized = this.problemsInfrastructure.getIsolatableSerializer().serialize(isolated);

        this.additionalData = new DefaultTypedAdditionalData(serializedBaseClass, serialized);
        return this;
    }

    @NonNull
    private <T extends AdditionalData> AdditionalData createAdditionalData(Class<T> type, Action<? super T> config) {
        T additionalDataInstance = problemsInfrastructure.getInstantiator().newInstance(type);
        config.execute(additionalDataInstance);
        return additionalDataInstance;
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
