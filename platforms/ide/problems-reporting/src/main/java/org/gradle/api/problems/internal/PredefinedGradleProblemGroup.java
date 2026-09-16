/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.problems.GradleProblemGroup;
import org.gradle.api.problems.GradleSecondLevelProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.UndefinedProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.Serializable;

/**
 * The predefined {@code Gradle} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedGradleProblemGroup extends GradleProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Gradle";

    private final DefaultGradleSecondLevelProblemGroup buildCache = new DefaultGradleSecondLevelProblemGroup("Build Cache", PredefinedProblemGroupDescriptions.GRADLE_BUILD_CACHE, this);
    private final DefaultGradleSecondLevelProblemGroup buildDefinition = new DefaultGradleSecondLevelProblemGroup("Build Definition", PredefinedProblemGroupDescriptions.GRADLE_BUILD_DEFINITION, this);
    private final DefaultGradleSecondLevelProblemGroup buildLogic = new DefaultGradleSecondLevelProblemGroup("Build Logic", PredefinedProblemGroupDescriptions.GRADLE_BUILD_LOGIC, this);
    private final DefaultGradleSecondLevelProblemGroup configurationCache = new DefaultGradleSecondLevelProblemGroup("Configuration Cache", PredefinedProblemGroupDescriptions.GRADLE_CONFIGURATION_CACHE, this);
    private final DefaultGradleSecondLevelProblemGroup deprecation = new DefaultGradleSecondLevelProblemGroup("Deprecation", PredefinedProblemGroupDescriptions.GRADLE_DEPRECATION, this);
    private final DefaultGradleSecondLevelProblemGroup dslEvaluation = new DefaultGradleSecondLevelProblemGroup("DSL Evaluation", PredefinedProblemGroupDescriptions.GRADLE_DSL_EVALUATION, this);
    private final DefaultGradleSecondLevelProblemGroup invocation = new DefaultGradleSecondLevelProblemGroup("Invocation", PredefinedProblemGroupDescriptions.GRADLE_INVOCATION, this);
    private final DefaultGradleSecondLevelProblemGroup isolatedProjects = new DefaultGradleSecondLevelProblemGroup("Isolated Projects", PredefinedProblemGroupDescriptions.GRADLE_ISOLATED_PROJECTS, this);
    private final DefaultGradleSecondLevelProblemGroup pluginValidation = new DefaultGradleSecondLevelProblemGroup("Plugin Validation", PredefinedProblemGroupDescriptions.GRADLE_PLUGIN_VALIDATION, this);
    private final DefaultUndefinedProblemGroup undefined = new DefaultUndefinedProblemGroup(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(buildCache, buildDefinition, buildLogic, configurationCache, deprecation, dslEvaluation, invocation, isolatedProjects, pluginValidation, undefined);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return PredefinedProblemGroupDescriptions.GRADLE;
    }

    @Override
    @Nullable
    public ProblemGroup getParent() {
        return null;
    }

    @Override
    @Nullable
    public ProblemGroupInternal getParentInternal() {
        return null;
    }

    @Override
    public GradleSecondLevelProblemGroup getBuildCache() {
        return buildCache;
    }

    @Override
    public GradleSecondLevelProblemGroup getBuildDefinition() {
        return buildDefinition;
    }

    @Override
    public GradleSecondLevelProblemGroup getBuildLogic() {
        return buildLogic;
    }

    @Override
    public GradleSecondLevelProblemGroup getConfigurationCache() {
        return configurationCache;
    }

    @Override
    public GradleSecondLevelProblemGroup getDeprecation() {
        return deprecation;
    }

    @Override
    public GradleSecondLevelProblemGroup getDslEvaluation() {
        return dslEvaluation;
    }

    @Override
    public GradleSecondLevelProblemGroup getInvocation() {
        return invocation;
    }

    @Override
    public GradleSecondLevelProblemGroup getIsolatedProjects() {
        return isolatedProjects;
    }

    @Override
    public GradleSecondLevelProblemGroup getPluginValidation() {
        return pluginValidation;
    }

    @Override
    public UndefinedProblemGroup getUndefined() {
        return undefined;
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) throws InvalidObjectException {
        return children.resolveClosed(name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return ProblemGroupSupport.equals(this, o);
    }

    @Override
    public int hashCode() {
        return ProblemGroupSupport.hashCode(this);
    }

    @Override
    public String toString() {
        return ProblemGroupSupport.render(this);
    }

    private Object writeReplace() {
        return SerializedProblemGroup.of(this);
    }
}
