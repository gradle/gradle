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
package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.plugins.ide.eclipse.model.internal.DefaultProjectModulePathResolver;
import org.gradle.plugins.ide.eclipse.model.internal.PrecomputedProjectModulePathResolver;
import org.gradle.plugins.ide.eclipse.model.internal.ProjectModulePathResolver;
import org.gradle.plugins.ide.internal.tooling.eclipse.IsolatedEclipseProjectInternal;
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gathers the module path flag of every project in the build tree, so that resolving the Eclipse
 * classpath of a project dependency does not have to read it from the depended-upon project.
 *
 * <p>The values are collected as {@link IsolatedEclipseProjectInternal} intermediate models, one per
 * project, which is what makes the whole model safe to build with Isolated Projects enabled. A project
 * dependency may be substituted from an included build, so the whole build tree is covered, not just
 * the build being built.
 */
@NullMarked
final class EclipseModulePathGatherer {

    private EclipseModulePathGatherer() {
    }

    /**
     * Returns a resolver backed by values gathered for the whole build tree, or one that reads them
     * live when no {@link IntermediateToolingModelProvider} is available. The latter happens in unit
     * tests that drive the builder directly; Isolated Projects is never enabled there.
     */
    static ProjectModulePathResolver resolverFor(ProjectState rootProjectState, @Nullable IntermediateToolingModelProvider modelProvider) {
        if (modelProvider == null) {
            return new DefaultProjectModulePathResolver();
        }

        Map<Path, Boolean> inferModulePathByBuildTreePath = new HashMap<>();
        for (BuildState build : buildsInTree(rootProjectState.getOwner())) {
            collectFromBuild(build, modelProvider, inferModulePathByBuildTreePath);
        }
        return new PrecomputedProjectModulePathResolver(inferModulePathByBuildTreePath);
    }

    private static void collectFromBuild(BuildState build, IntermediateToolingModelProvider modelProvider, Map<Path, Boolean> result) {
        List<ProjectState> projects = ImmutableList.copyOf(build.getProjects().getAllProjects());
        if (projects.isEmpty()) {
            return;
        }

        List<IsolatedEclipseProjectInternal> models = modelProvider.getModels(
            build.getProjects().getRootProject(), projects, IsolatedEclipseProjectInternal.class, null);

        // getModels returns one model per requested project, in the order they were requested.
        for (int i = 0; i < projects.size(); i++) {
            result.put(projects.get(i).getIdentity().getBuildTreePath(), models.get(i).isInferModulePath());
        }
    }

    private static Set<BuildState> buildsInTree(BuildState root) {
        Set<BuildState> builds = new LinkedHashSet<>();
        collectBuilds(root, builds);
        return builds;
    }

    private static void collectBuilds(BuildState build, Set<BuildState> builds) {
        if (!builds.add(build)) {
            return; // included builds can form a cycle
        }
        for (IncludedBuildInternal reference : build.getMutableModel().includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                target.ensureProjectsConfigured();
                collectBuilds(target, builds);
            }
        }
    }
}
