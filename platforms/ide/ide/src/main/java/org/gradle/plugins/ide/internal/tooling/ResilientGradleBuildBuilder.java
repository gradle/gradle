/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.composite.BrokenIncludedBuildState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.kotlin.dsl.support.ScriptCompilationException;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

@NullMarked
public class ResilientGradleBuildBuilder extends GradleBuildBuilder {
    public ResilientGradleBuildBuilder(BuildStateRegistry buildStateRegistry) {
        super(buildStateRegistry);
    }

    @Override
    protected Throwable ensureProjectsLoaded(BuildState target) {
        try {
            target.ensureProjectsLoaded();
            return null;
        } catch (LocationAwareException e) {
            System.err.println(e.getMessage());
            if (e.getCause() instanceof ScriptCompilationException) {
                return e.getCause();
            }
            throw e;
        }
    }

    @Override
    protected DefaultGradleBuild convert(BuildState targetBuild, Map<BuildState, DefaultGradleBuild> all) {
        DefaultGradleBuild model = all.get(targetBuild);
        if (model != null) {
            return model;
        }
        model = new DefaultGradleBuild();
        all.put(targetBuild, model);

        // Make sure the project tree has been loaded and can be queried (but not necessarily configured)
        ensureProjectsLoaded(targetBuild);
        if (targetBuild instanceof BrokenIncludedBuildState) {
            Failure failure = ((BrokenIncludedBuildState) targetBuild).getFailure();
            model.setFailure(DefaultFailure.fromFailure(failure, unused -> null));
        }

        GradleInternal gradle = targetBuild.getMutableModel();
        if (targetBuild.isProjectsLoaded()) {
            addProjects(targetBuild, model);
        }
        try {
            addIncludedBuilds(gradle, model, all);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        }
        iterateParents(targetBuild, all, gradle, model);
        return model;
    }

    @Override
    protected void addIncludedBuilds(GradleInternal gradle, DefaultGradleBuild model, Map<BuildState, DefaultGradleBuild> all) {
        for (IncludedBuildInternal reference : gradle.includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target != null) {
                model.addIncludedBuild(convert(target, all));
            }
        }
    }
}
