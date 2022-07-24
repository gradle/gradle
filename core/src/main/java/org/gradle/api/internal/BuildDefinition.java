/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.internal.Actions;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.plugin.management.internal.PluginRequests;

import javax.annotation.Nullable;
import java.io.File;

public class BuildDefinition {
    @Nullable
    private final String name;
    @Nullable
    private final File buildRootDir;
    private final StartParameterInternal startParameter;
    private final PluginRequests injectedSettingsPlugins;
    private final Action<? super DependencySubstitutions> dependencySubstitutions;
    private final PublicBuildPath fromBuild;
    private final boolean pluginBuild;

    private BuildDefinition(
        @Nullable String name,
        @Nullable File buildRootDir,
        StartParameterInternal startParameter,
        PluginRequests injectedSettingsPlugins,
        Action<? super DependencySubstitutions> dependencySubstitutions,
        PublicBuildPath fromBuild,
        boolean pluginBuild) {
        this.name = name;
        this.buildRootDir = buildRootDir;
        this.startParameter = startParameter;
        this.injectedSettingsPlugins = injectedSettingsPlugins;
        this.dependencySubstitutions = dependencySubstitutions;
        this.fromBuild = fromBuild;
        this.pluginBuild = pluginBuild;
    }

    /**
     * Returns a name to use for this build. Use {@code null} to have a name assigned.
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * Returns the root directory for this build, when known.
     */
    @Nullable
    public File getBuildRootDir() {
        return buildRootDir;
    }

    /**
     * The identity of the build that caused this build to be included.
     *
     * This is not guaranteed to be the parent build WRT the build path, or Gradle instance.
     *
     * Null if the build is the root build.
     */
    @Nullable
    public PublicBuildPath getFromBuild() {
        return fromBuild;
    }

    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    public PluginRequests getInjectedPluginRequests() {
        return injectedSettingsPlugins;
    }

    public Action<? super DependencySubstitutions> getDependencySubstitutions() {
        return dependencySubstitutions;
    }

    public boolean isPluginBuild() {
        return pluginBuild;
    }

    public static BuildDefinition fromStartParameterForBuild(
        StartParameterInternal startParameter,
        String name,
        File buildRootDir,
        PluginRequests pluginRequests,
        Action<? super DependencySubstitutions> dependencySubstitutions,
        PublicBuildPath fromBuild,
        boolean pluginBuild
    ) {
        return new BuildDefinition(
            name,
            buildRootDir,
            startParameterForIncludedBuildFrom(startParameter, buildRootDir),
            pluginRequests,
            dependencySubstitutions,
            fromBuild,
            pluginBuild);
    }

    public static BuildDefinition fromStartParameter(StartParameterInternal startParameter, @Nullable PublicBuildPath fromBuild) {
        return fromStartParameter(startParameter, null, fromBuild);
    }

    public static BuildDefinition fromStartParameter(StartParameterInternal startParameter, @Nullable File rootBuildDir, @Nullable PublicBuildPath fromBuild) {
        return new BuildDefinition(null, rootBuildDir, startParameter, PluginRequests.EMPTY, Actions.doNothing(), fromBuild, false);
    }

    private static StartParameterInternal startParameterForIncludedBuildFrom(StartParameterInternal startParameter, File buildRootDir) {
        StartParameterInternal includedBuildStartParam = startParameter.newBuild();
        includedBuildStartParam.setCurrentDir(buildRootDir);
        includedBuildStartParam.doNotSearchUpwards();
        includedBuildStartParam.setInitScripts(startParameter.getInitScripts());
        includedBuildStartParam.setExcludedTaskNames(startParameter.getExcludedTaskNames());
        return includedBuildStartParam;
    }

    /**
     * Creates a defensive copy of this build definition, to isolate this instance from mutations made to the {@link StartParameter} during execution of the build.
     */
    public BuildDefinition newInstance() {
        return new BuildDefinition(name, buildRootDir, startParameter.newInstance(), injectedSettingsPlugins, dependencySubstitutions, fromBuild, pluginBuild);
    }
}
