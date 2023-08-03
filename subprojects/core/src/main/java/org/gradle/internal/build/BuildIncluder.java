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

package org.gradle.internal.build;

import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;

/**
 * Coordinates inclusion of builds from the current build.
 */
@ServiceScope(Scopes.Build.class)
public interface BuildIncluder {
    /**
     * Registers an included build of the current build. An included build may provide plugins and libraries to this build and all other builds in the tree.
     */
    CompositeBuildParticipantBuildState includeBuild(IncludedBuildSpec includedBuildSpec);

    /**
     * Registers an included plugin build of the current build. An included plugin build may provide plugins to this build only. In contrast to {@link #includeBuild(IncludedBuildSpec)},
     * this method does not make any libraries visible to this build, nor does it make anything visible to other builds in the tree.
     */
    void registerPluginBuild(IncludedBuildSpec includedBuildSpec);

    /**
     * Returns the set of plugin builds for this build. These are registered using {@link #registerPluginBuild(IncludedBuildSpec)}.
     */
    Collection<IncludedBuildState> getRegisteredPluginBuilds();

    /**
     * Returns the set of included builds that are visible to this build for plugin resolution.
     */
    Collection<IncludedBuildState> getIncludedBuildsForPluginResolution();

    /**
     * Prepares an included build so that the plugins it provides can be resolved.
     */
    void prepareForPluginResolution(IncludedBuildState build);
}
