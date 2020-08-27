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
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class BuildOperationFiringProjectsPreparer implements ProjectsPreparer {
    private static final ConfigureBuildBuildOperationType.Result CONFIGURE_BUILD_RESULT = new ConfigureBuildBuildOperationType.Result() {
    };
    private final ProjectsPreparer delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationFiringProjectsPreparer(ProjectsPreparer delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        buildOperationExecutor.run(new ConfigureBuild(gradle));
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
            if (gradle.isRootBuild()) {
                builder.metadata(BuildOperationCategory.CONFIGURE_ROOT_BUILD);
            } else {
                builder.metadata(BuildOperationCategory.CONFIGURE_BUILD);
            }
            builder.totalProgress(gradle.getSettings().getProjectRegistry().size());
            return builder.details(new ConfigureBuildBuildOperationType.Details() {
                @Override
                public String getBuildPath() {
                    return gradle.getIdentityPath().toString();
                }
            });
        }
    }
}
