/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;

public class NotifyingBuildLoader implements BuildLoader {
    private static final NotifyProjectsLoadedBuildOperationType.Result PROJECTS_LOADED_OP_RESULT = new NotifyProjectsLoadedBuildOperationType.Result() {
    };

    private final BuildLoader buildLoader;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOperationProgressEventEmitter emitter;

    public NotifyingBuildLoader(BuildLoader buildLoader, BuildOperationExecutor buildOperationExecutor, BuildOperationProgressEventEmitter emitter) {
        this.buildLoader = buildLoader;
        this.buildOperationExecutor = buildOperationExecutor;
        this.emitter = emitter;
    }

    @Override
    public void load(final SettingsInternal settings, final GradleInternal gradle) {
        final String buildPath = gradle.getIdentityPath().toString();
        buildOperationExecutor.call(new CallableBuildOperation<Void>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                //noinspection Convert2Lambda
                return BuildOperationDescriptor
                    .displayName("Load projects")
                    .progressDisplayName("Loading projects")
                    .details(new LoadProjectsBuildOperationType.Details() {
                        @Override
                        public String getBuildPath() {
                            return buildPath;
                        }
                    });
            }

            @Override
            public Void call(BuildOperationContext context) {
                buildLoader.load(settings, gradle);
                BuildStructureOperationProject rootProject = BuildStructureOperationProject.from(gradle);
                emitter.emitNowForCurrent(new DefaultProjectsIdentifiedProgressDetails(rootProject, buildPath));
                context.setResult(new BuildStructureOperationResult(rootProject, buildPath));
                return null;
            }
        });
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                gradle.getBuildListenerBroadcaster().projectsLoaded(gradle);
                context.setResult(PROJECTS_LOADED_OP_RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                //noinspection Convert2Lambda
                return BuildOperationDescriptor
                    .displayName(gradle.contextualize("Notify projectsLoaded listeners"))
                    .details(new NotifyProjectsLoadedBuildOperationType.Details() {
                        @Override
                        public String getBuildPath() {
                            return buildPath;
                        }
                    });
            }
        });
    }

    private static class BuildStructureOperationResult implements LoadProjectsBuildOperationType.Result {
        private final LoadProjectsBuildOperationType.Result.Project rootProject;
        private final String buildPath;

        public BuildStructureOperationResult(LoadProjectsBuildOperationType.Result.Project rootProject, String buildPath) {
            this.rootProject = rootProject;
            this.buildPath = buildPath;
        }

        @Override
        public LoadProjectsBuildOperationType.Result.Project getRootProject() {
            return rootProject;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }
    }

    private static class DefaultProjectsIdentifiedProgressDetails implements ProjectsIdentifiedProgressDetails {
        private final String buildPath;
        private final Project rootProject;

        public DefaultProjectsIdentifiedProgressDetails(Project rootProject, String buildPath) {
            this.buildPath = buildPath;
            this.rootProject = rootProject;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }

        @Override
        public Project getRootProject() {
            return rootProject;
        }
    }
}
