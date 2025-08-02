/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.buildtree.BuildInclusionCoordinator;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.internal.reflect.Instantiator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ResilientBuildIncluder extends DefaultBuildIncluder {

    private final FailureFactory failureFactory;

    public ResilientBuildIncluder(
        BuildStateRegistry buildRegistry,
        BuildInclusionCoordinator coordinator,
        PublicBuildPath publicBuildPath,
        Instantiator instantiator,
        GradleInternal gradle,
        FailureFactory failureFactory
    ) {
        super(buildRegistry, coordinator, publicBuildPath, instantiator, gradle);
        this.failureFactory = failureFactory;
    }

    @Override
    protected IncludedBuildState prepareBuildState(IncludedBuildState build, BuildDefinition buildDefinition) {
        try {
            coordinator.prepareForInclusion(build, buildDefinition.isPluginBuild());
        } catch (LocationAwareException e) {
            if(e.getCause().getClass().getName().equals("org.gradle.kotlin.dsl.support.ScriptCompilationException")) {
                Failure failure = failureFactory.create(e.getCause());
                BrokenIncludedBuildState brokenIncludedBuildState = new BrokenIncludedBuildState(build, failure);
                buildRegistry.replaceIncludedBuild(brokenIncludedBuildState, build);
                return brokenIncludedBuildState;
            } else {
                throw e;
            }
        }
        return build;
    }


}
