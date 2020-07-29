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

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.RunNestedBuildBuildOperationType;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.invocation.GradleBuildController;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.util.Path;

import java.io.File;

public class RootOfNestedBuildTree extends AbstractBuildState implements NestedRootBuild {
    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildState owner;
    private final GradleLauncher gradleLauncher;
    private String buildName;

    public RootOfNestedBuildTree(BuildDefinition buildDefinition, BuildIdentifier buildIdentifier, Path identityPath, BuildState owner) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.owner = owner;
        this.buildName = buildDefinition.getName() == null ? buildIdentifier.getName() : buildDefinition.getName();
        this.gradleLauncher = owner.getNestedBuildFactory().nestedBuildTree(buildDefinition, this);
    }

    public void attach() {
        gradleLauncher.getGradle().getServices().get(BuildStateRegistry.class).attachRootBuild(this);
    }

    @Override
    public StartParameter getStartParameter() {
        return gradleLauncher.getGradle().getStartParameter();
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
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
    public NestedBuildFactory getNestedBuildFactory() {
        return gradleLauncher.getGradle().getServices().get(NestedBuildFactory.class);
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return owner.getCurrentPrefixForProjectsInChildBuilds().child(buildName);
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return gradleLauncher.getGradle().getIdentityPath().append(projectPath);
    }

    @Override
    public File getBuildRootDir() {
        return gradleLauncher.getBuildRootDir();
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
                    gradle.addBuildListener(new InternalBuildAdapter() {
                        @Override
                        public void settingsEvaluated(Settings settings) {
                            buildName = settings.getRootProject().getName();
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


