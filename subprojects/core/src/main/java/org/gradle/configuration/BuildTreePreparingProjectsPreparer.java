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

package org.gradle.configuration;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class BuildTreePreparingProjectsPreparer implements ProjectsPreparer {
    private final ProjectsPreparer delegate;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildStateRegistry buildStateRegistry;
    private final BuildSourceBuilder buildSourceBuilder;

    public BuildTreePreparingProjectsPreparer(ProjectsPreparer delegate, BuildOperationExecutor buildOperationExecutor, BuildStateRegistry buildStateRegistry, BuildSourceBuilder buildSourceBuilder) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildStateRegistry = buildStateRegistry;
        this.buildSourceBuilder = buildSourceBuilder;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        if (gradle.isRootBuild()) {
            buildOperationExecutor.run(new PrepareBuildTree(gradle));
            buildStateRegistry.afterConfigureRootBuild();
        } else {
            buildBuildSrcAndPrepareProjects(gradle);
        }
    }

    private void buildBuildSrcAndPrepareProjects(GradleInternal gradle) {
        ClassLoaderScope baseProjectClassLoaderScope = buildSourceBuilder.buildAndCreateClassLoader(gradle);
        gradle.setBaseProjectClassLoaderScope(baseProjectClassLoaderScope);
        delegate.prepareProjects(gradle);
    }

    private class PrepareBuildTree implements RunnableBuildOperation {
        private final GradleInternal gradle;

        public PrepareBuildTree(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public void run(BuildOperationContext context) {
            buildStateRegistry.beforeConfigureRootBuild();
            buildBuildSrcAndPrepareProjects(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Prepare build tree"));
            return builder;
        }
    }
}
