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

package org.gradle.composite.internal;

import org.gradle.BuildAdapter;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.RunNestedBuildBuildOperationType;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.invocation.GradleBuildController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.util.Path;

public class RootOfNestedBuildTree implements StandAloneNestedBuild {
    private final BuildIdentifier buildIdentifier;
    private final GradleLauncher gradleLauncher;

    public RootOfNestedBuildTree(BuildDefinition buildDefinition, BuildIdentifier buildIdentifier, NestedBuildFactory buildFactory) {
        this.buildIdentifier = buildIdentifier;
        this.gradleLauncher = buildFactory.nestedBuildTree(buildDefinition, buildIdentifier);
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public boolean isImplicitBuild() {
        return false;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return gradleLauncher.getGradle().getSettings();
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return gradleLauncher.getGradle().getIdentityPath().append(projectPath);
    }

    @Override
    public <T> T run(final Transformer<T, ? super BuildController> buildAction) {
        final BuildController buildController = new GradleBuildController(gradleLauncher);
        try {
            final GradleInternal gradle = gradleLauncher.getGradle();
            BuildOperationExecutor executor = gradle.getServices().get(BuildOperationExecutor.class);
            return executor.call(new CallableBuildOperation<T>() {
                @Override
                public T call(BuildOperationContext context) {
                    gradle.addBuildListener(new BuildAdapter() {
                        @Override
                        public void projectsLoaded(Gradle g) {
                            gradle.getServices().get(ProjectStateRegistry.class).registerProjects(RootOfNestedBuildTree.this);
                        }
                    });
                    T result = buildAction.transform(buildController);
                    context.setResult(new RunNestedBuildBuildOperationType.Result() {
                    });
                    return result;
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Run nested build")
                        .details(new RunNestedBuildBuildOperationType.Details() {
                            @Override
                            public String getBuildPath() {
                                return gradle.getIdentityPath().getPath();
                            }
                        });
                }
            });
        } finally {
            buildController.stop();
        }
    }
}


