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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.initialization.RunNestedBuildBuildOperationType;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.Pair;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.BuildWorkGraph;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.buildtree.BuildTreeFinishExecutor;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory;
import org.gradle.internal.buildtree.BuildTreeModelControllerServices;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.BuildTreeWorkExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeFinishExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeWorkExecutor;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.session.BuildSessionState;
import org.gradle.util.Path;

import java.io.File;
import java.util.Set;
import java.util.function.Function;

public class RootOfNestedBuildTree extends AbstractBuildState implements NestedRootBuild {
    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildState owner;
    private final BuildLifecycleController buildLifecycleController;
    private final BuildTreeLifecycleController buildTreeLifecycleController;
    private String buildName;
    private final BuildTreeState buildTree;
    private final BuildScopeServices buildServices;

    public RootOfNestedBuildTree(
        BuildDefinition buildDefinition,
        BuildIdentifier buildIdentifier,
        Path identityPath,
        BuildState owner,
        BuildSessionState buildSessionState
    ) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.owner = owner;
        this.buildName = buildDefinition.getName() == null ? buildIdentifier.getName() : buildDefinition.getName();

        StartParameterInternal startParameter = buildDefinition.getStartParameter();

        BuildTreeModelControllerServices.Supplier modelServices = buildSessionState.getServices().get(BuildTreeModelControllerServices.class).servicesForNestedBuildTree(startParameter);
        buildTree = new BuildTreeState(buildSessionState.getServices(), modelServices);

        // Create the controllers using the services of the nested tree
        BuildLifecycleControllerFactory buildLifecycleControllerFactory = buildTree.getServices().get(BuildLifecycleControllerFactory.class);
        buildServices = new BuildScopeServices(buildTree.getServices());
        this.buildLifecycleController = buildLifecycleControllerFactory.newInstance(buildDefinition, this, owner, buildServices);

        BuildTreeLifecycleControllerFactory buildTreeLifecycleControllerFactory = buildServices.get(BuildTreeLifecycleControllerFactory.class);
        IncludedBuildTaskGraph controllers = buildServices.get(IncludedBuildTaskGraph.class);
        ExceptionAnalyser exceptionAnalyser = buildServices.get(ExceptionAnalyser.class);
        BuildStateRegistry buildStateRegistry = buildServices.get(BuildStateRegistry.class);
        BuildTreeWorkExecutor buildTreeWorkExecutor = new DefaultBuildTreeWorkExecutor(controllers, buildLifecycleController);
        BuildTreeFinishExecutor buildTreeFinishExecutor = new DefaultBuildTreeFinishExecutor(buildStateRegistry, exceptionAnalyser, buildLifecycleController);
        buildTreeLifecycleController = buildTreeLifecycleControllerFactory.createController(buildLifecycleController, buildTreeWorkExecutor, buildTreeFinishExecutor);
    }

    public void attach() {
        buildServices.get(BuildStateRegistry.class).attachRootBuild(this);
    }

    @Override
    protected BuildLifecycleController getBuildController() {
        return buildLifecycleController;
    }

    @Override
    public BuildWorkGraph getWorkGraph() {
        return buildServices.get(BuildWorkGraph.class);
    }

    @Override
    protected ProjectStateRegistry getProjectStateRegistry() {
        return buildServices.get(ProjectStateRegistry.class);
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
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return owner.getCurrentPrefixForProjectsInChildBuilds().child(buildName);
    }

    @Override
    public Path calculateIdentityPathForProject(Path projectPath) {
        return buildLifecycleController.getGradle().getIdentityPath().append(projectPath);
    }

    @Override
    public File getBuildRootDir() {
        return buildLifecycleController.getGradle().getServices().get(BuildLayout.class).getRootDirectory();
    }

    @Override
    public IncludedBuildInternal getModel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProjectComponentIdentifier idToReferenceProjectFromAnotherBuild(ProjectComponentIdentifier identifier) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T run(Function<? super BuildTreeLifecycleController, T> action) {
        try {
            final GradleInternal gradle = buildLifecycleController.getGradle();
            ServiceRegistry services = gradle.getServices();
            BuildOperationExecutor executor = services.get(BuildOperationExecutor.class);
            return executor.call(new CallableBuildOperation<T>() {
                @Override
                public T call(BuildOperationContext context) {
                    gradle.addBuildListener(new InternalBuildAdapter() {
                        @Override
                        public void settingsEvaluated(Settings settings) {
                            buildName = settings.getRootProject().getName();
                        }
                    });
                    T result = action.apply(buildTreeLifecycleController);
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
            CompositeStoppable.stoppable(buildLifecycleController, buildTree).stop();
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


