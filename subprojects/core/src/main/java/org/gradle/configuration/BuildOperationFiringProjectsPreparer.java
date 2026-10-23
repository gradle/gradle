/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.initialization.ConfigureBuildBuildOperationType;
import org.gradle.internal.buildtree.RootBuildConfigurationProgressHelper;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;

public class BuildOperationFiringProjectsPreparer implements ProjectsPreparer {
    private static final ConfigureBuildBuildOperationType.Result CONFIGURE_BUILD_RESULT = new ConfigureBuildBuildOperationType.Result() {
    };
    private final ProjectsPreparer delegate;
    private final BuildOperationRunner buildOperationRunner;
    private final RootBuildConfigurationProgressHelper rootBuildConfigurationProgressHelper;

    public BuildOperationFiringProjectsPreparer(ProjectsPreparer delegate, BuildOperationRunner buildOperationRunner, RootBuildConfigurationProgressHelper rootBuildConfigurationProgressHelper) {
        this.delegate = delegate;
        this.buildOperationRunner = buildOperationRunner;
        this.rootBuildConfigurationProgressHelper = rootBuildConfigurationProgressHelper;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        buildOperationRunner.run(new ConfigureBuild(gradle));
    }

    private class ConfigureBuild implements RunnableBuildOperation {
        private final GradleInternal gradle;

        public ConfigureBuild(GradleInternal gradle) {
            this.gradle = gradle;
        }

        @Override
        public void run(BuildOperationContext context) {
            delegate.prepareProjects(gradle);
            context.setResult(CONFIGURE_BUILD_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Configure build"));
            int projectCount = gradle.getSettings().getProjectRegistry().size();
            if (gradle.isRootBuild()) {
                builder.metadata(BuildOperationCategory.CONFIGURE_ROOT_BUILD);
                builder.totalProgress(projectCount + rootBuildConfigurationProgressHelper.getAdditionalTotalProgress());
            } else {
                builder.metadata(BuildOperationCategory.CONFIGURE_BUILD);
                builder.totalProgress(projectCount);
            }
            return builder.details(new ConfigureBuildBuildOperationType.Details() {
                @Override
                public String getBuildPath() {
                    return gradle.getIdentityPath().toString();
                }

                @Override
                public int getProjectCount() {
                    return projectCount;
                }
            });
        }
    }
}
