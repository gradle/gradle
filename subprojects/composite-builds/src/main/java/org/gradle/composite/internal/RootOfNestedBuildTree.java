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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.RunNestedBuildBuildOperationType;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.buildtree.BuildTreeController;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeModelControllerServices;
import org.gradle.internal.buildtree.BuildTreeWorkExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController;
import org.gradle.internal.buildtree.DefaultBuildTreeWorkExecutor;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.session.BuildSessionController;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.internal.time.Time;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Function;

public class RootOfNestedBuildTree extends AbstractBuildState implements NestedRootBuild {
    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildState owner;
    private final BuildLifecycleController buildLifecycleController;
    private String buildName;
    private final BuildSessionController session;
    private final BuildTreeController buildTree;

    public RootOfNestedBuildTree(BuildDefinition buildDefinition,
                                 BuildIdentifier buildIdentifier,
                                 Path identityPath,
                                 BuildState owner,
                                 BuildModelControllerServices buildModelControllerServices,
                                 GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry,
                                 CrossBuildSessionState crossBuildSessionState,
                                 BuildCancellationToken buildCancellationToken) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.owner = owner;
        this.buildName = buildDefinition.getName() == null ? buildIdentifier.getName() : buildDefinition.getName();

        StartParameterInternal startParameter = buildDefinition.getStartParameter();
        BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(Time.currentTimeMillis());
        session = new BuildSessionController(userHomeDirServiceRegistry, crossBuildSessionState, startParameter, buildRequestMetaData, ClassPath.EMPTY, buildCancellationToken, buildRequestMetaData.getClient(), new NoOpBuildEventConsumer());
        BuildTreeModelControllerServices.Supplier modelServices = session.getServices().get(BuildTreeModelControllerServices.class).servicesForNestedBuildTree(startParameter);
        buildTree = new BuildTreeController(session.getServices(), modelServices);
        // Create the controller using the services of the nested tree
        BuildLifecycleControllerFactory buildLifecycleControllerFactory = buildTree.getServices().get(BuildLifecycleControllerFactory.class);
        BuildScopeServices buildScopeServices = new BuildScopeServices(buildTree.getServices());
        buildModelControllerServices.supplyBuildScopeServices(buildScopeServices);
        this.buildLifecycleController = buildLifecycleControllerFactory.newInstance(buildDefinition, this, owner.getMutableModel(), buildScopeServices);
    }

    public void attach() {
        buildLifecycleController.getGradle().getServices().get(BuildStateRegistry.class).attachRootBuild(this);
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return buildLifecycleController.getGradle().getStartParameter();
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
        return buildLifecycleController.getGradle().getSettings();
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return owner.getCurrentPrefixForProjectsInChildBuilds().child(buildName);
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return buildLifecycleController.getGradle().getIdentityPath().append(projectPath);
    }

    @Override
    public File getBuildRootDir() {
        return buildLifecycleController.getGradle().getServices().get(BuildLayout.class).getRootDirectory();
    }

    @Override
    public <T> T run(Function<? super BuildTreeLifecycleController, T> action) {
        try {
            final GradleInternal gradle = buildLifecycleController.getGradle();
            BuildOperationExecutor executor = gradle.getServices().get(BuildOperationExecutor.class);
            IncludedBuildControllers controllers = gradle.getServices().get(IncludedBuildControllers.class);
            WorkerLeaseService workerLeaseService = gradle.getServices().get(WorkerLeaseService.class);
            ExceptionAnalyser exceptionAnalyser = gradle.getServices().get(ExceptionAnalyser.class);
            BuildTreeWorkExecutor buildTreeWorkExecutor = new DefaultBuildTreeWorkExecutor(controllers, buildLifecycleController);
            final DefaultBuildTreeLifecycleController buildController = new DefaultBuildTreeLifecycleController(buildLifecycleController, workerLeaseService, buildTreeWorkExecutor, controllers, exceptionAnalyser);
            return executor.call(new CallableBuildOperation<T>() {
                @Override
                public T call(BuildOperationContext context) {
                    gradle.addBuildListener(new InternalBuildAdapter() {
                        @Override
                        public void settingsEvaluated(Settings settings) {
                            buildName = settings.getRootProject().getName();
                        }
                    });
                    T result = action.apply(buildController);
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
            CompositeStoppable.stoppable(buildLifecycleController, buildTree, session).stop();
        }
    }

    @Override
    public GradleInternal getBuild() {
        return buildLifecycleController.getGradle();
    }

    @Override
    public GradleInternal getMutableModel() {
        return buildLifecycleController.getGradle();
    }
}


