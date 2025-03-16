/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.composite;

import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildInclusionCoordinator;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultBuildIncluder implements BuildIncluder {

    private final BuildStateRegistry buildRegistry;
    private final BuildInclusionCoordinator coordinator;
    private final PublicBuildPath publicBuildPath;
    private final Instantiator instantiator;
    private final GradleInternal gradle;
    private final List<BuildDefinition> pluginBuildDefinitions = new ArrayList<>();

    public DefaultBuildIncluder(BuildStateRegistry buildRegistry, BuildInclusionCoordinator coordinator, PublicBuildPath publicBuildPath, Instantiator instantiator, GradleInternal gradle) {
        this.buildRegistry = buildRegistry;
        this.coordinator = coordinator;
        this.publicBuildPath = publicBuildPath;
        this.instantiator = instantiator;
        this.gradle = gradle;
    }

    @Override
    public CompositeBuildParticipantBuildState includeBuild(IncludedBuildSpec includedBuildSpec) {
        RootBuildState rootBuild = buildRegistry.getRootBuild();
        BuildDefinition buildDefinition = toBuildDefinition(includedBuildSpec, gradle);
        if (includedBuildSpec.rootDir.equals(rootBuild.getBuildRootDir())) {
            buildRegistry.onRootBuildInclude(rootBuild, gradle.getOwner(), buildDefinition.isPluginBuild());
            coordinator.prepareRootBuildForInclusion();
            return rootBuild;
        } else {
            IncludedBuildState build = buildRegistry.addIncludedBuild(buildDefinition, gradle.getOwner());
            coordinator.prepareForInclusion(build, buildDefinition.isPluginBuild());
            return build;
        }
    }

    @Override
    public void registerPluginBuild(IncludedBuildSpec includedBuildSpec) {
        pluginBuildDefinitions.add(toBuildDefinition(includedBuildSpec, gradle));
    }

    @Override
    public Collection<IncludedBuildState> getRegisteredPluginBuilds() {
        return pluginBuildDefinitions.stream().map(buildDefinition -> {
            IncludedBuildState build = buildRegistry.addIncludedBuild(buildDefinition, gradle.getOwner());
            coordinator.prepareForInclusion(build, true);
            return build;
        }).collect(Collectors.toList());
    }

    @Override
    public Collection<IncludedBuildState> getIncludedBuildsForPluginResolution() {
        BuildState thisBuild = gradle.getOwner();
        return buildRegistry.getIncludedBuilds().stream().filter(build ->
            build != thisBuild && !build.isImplicitBuild() && !build.isPluginBuild()
        ).collect(Collectors.toList());
    }

    @Override
    public void prepareForPluginResolution(IncludedBuildState build) {
        coordinator.prepareForPluginResolution(build);
    }

    private BuildDefinition toBuildDefinition(IncludedBuildSpec includedBuildSpec, GradleInternal gradle) {
        gradle.getOwner().assertCanAdd(includedBuildSpec);
        return includedBuildSpec.toBuildDefinition(gradle.getStartParameter(), publicBuildPath, instantiator);
    }
}
