/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Coordinates inclusion of builds across the build tree.
 */
@ServiceScope(Scopes.BuildTree.class)
public class BuildInclusionCoordinator {
    private final Set<IncludedBuildState> loadedBuilds = new CopyOnWriteArraySet<>();
    private final List<IncludedBuildState> libraryBuilds = new CopyOnWriteArrayList<>();
    private final BuildStateRegistry buildStateRegistry;

    public BuildInclusionCoordinator(BuildStateRegistry buildStateRegistry) {
        this.buildStateRegistry = buildStateRegistry;
    }

    public void prepareForInclusion(IncludedBuildState build, boolean asPlugin) {
        if (loadedBuilds.add(build)) {
            // Load projects (eg by running the settings script, etc) only the first time the build is included by another build.
            // This is to deal with cycles and the build being included multiple times in the tree
            build.ensureProjectsLoaded();
        }
        if (!asPlugin && !libraryBuilds.contains(build)) {
            libraryBuilds.add(build);
        }
    }

    public void registerGlobalLibrarySubstitutions() {
        for (IncludedBuildState includedBuild : libraryBuilds) {
            buildStateRegistry.registerSubstitutionsFor(includedBuild);
        }
    }
}
