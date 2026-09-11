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
import org.gradle.api.problems.LeafProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.InvalidObjectException;
import java.io.Serializable;

/**
 * The predefined {@code Gradle} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedGradleProblemGroup extends GradleProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Gradle";

    private final DefaultLeafProblemGroup buildCache = new DefaultLeafProblemGroup("Build Cache", PredefinedProblemGroupDescriptions.GRADLE_BUILD_CACHE, this);
    private final DefaultLeafProblemGroup buildDefinition = new DefaultLeafProblemGroup("Build Definition", PredefinedProblemGroupDescriptions.GRADLE_BUILD_DEFINITION, this);
    private final DefaultLeafProblemGroup buildLogic = new DefaultLeafProblemGroup("Build Logic", PredefinedProblemGroupDescriptions.GRADLE_BUILD_LOGIC, this);
    private final DefaultLeafProblemGroup configurationCache = new DefaultLeafProblemGroup("Configuration Cache", PredefinedProblemGroupDescriptions.GRADLE_CONFIGURATION_CACHE, this);
    private final DefaultLeafProblemGroup deprecation = new DefaultLeafProblemGroup("Deprecation", PredefinedProblemGroupDescriptions.GRADLE_DEPRECATION, this);
    private final DefaultLeafProblemGroup dslEvaluation = new DefaultLeafProblemGroup("DSL Evaluation", PredefinedProblemGroupDescriptions.GRADLE_DSL_EVALUATION, this);
    private final DefaultLeafProblemGroup invocation = new DefaultLeafProblemGroup("Invocation", PredefinedProblemGroupDescriptions.GRADLE_INVOCATION, this);
    private final DefaultLeafProblemGroup isolatedProjects = new DefaultLeafProblemGroup("Isolated Projects", PredefinedProblemGroupDescriptions.GRADLE_ISOLATED_PROJECTS, this);
    private final DefaultLeafProblemGroup pluginValidation = new DefaultLeafProblemGroup("Plugin Validation", PredefinedProblemGroupDescriptions.GRADLE_PLUGIN_VALIDATION, this);
    private final DefaultLeafProblemGroup undefined = DefaultLeafProblemGroup.undefined(this, NAME);
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
    public LeafProblemGroup getBuildCache() {
        return buildCache;
    }

    @Override
    public LeafProblemGroup getBuildDefinition() {
        return buildDefinition;
    }

    @Override
    public LeafProblemGroup getBuildLogic() {
        return buildLogic;
    }

    @Override
    public LeafProblemGroup getConfigurationCache() {
        return configurationCache;
    }

    @Override
    public LeafProblemGroup getDeprecation() {
        return deprecation;
    }

    @Override
    public LeafProblemGroup getDslEvaluation() {
        return dslEvaluation;
    }

    @Override
    public LeafProblemGroup getInvocation() {
        return invocation;
    }

    @Override
    public LeafProblemGroup getIsolatedProjects() {
        return isolatedProjects;
    }

    @Override
    public LeafProblemGroup getPluginValidation() {
        return pluginValidation;
    }

    @Override
    public LeafProblemGroup getUndefined() {
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
