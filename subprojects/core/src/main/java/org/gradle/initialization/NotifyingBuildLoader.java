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

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Comparator;
import java.util.Set;

public class NotifyingBuildLoader implements BuildLoader {

    private static final NotifyProjectsLoadedBuildOperationType.Result PROJECTS_LOADED_OP_RESULT = new NotifyProjectsLoadedBuildOperationType.Result() {
    };

    private final BuildLoader buildLoader;
    private final BuildOperationExecutor buildOperationExecutor;

    public NotifyingBuildLoader(BuildLoader buildLoader, BuildOperationExecutor buildOperationExecutor) {
        this.buildLoader = buildLoader;
        this.buildOperationExecutor = buildOperationExecutor;
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
                context.setResult(createOperationResult(gradle, buildPath));
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

    private BuildStructureOperationResult createOperationResult(GradleInternal gradle, String buildPath) {
        LoadProjectsBuildOperationType.Result.Project rootProject = convert(gradle.getRootProject());
        return new BuildStructureOperationResult(rootProject, buildPath);
    }

    private LoadProjectsBuildOperationType.Result.Project convert(org.gradle.api.Project project) {
        return new BuildStructureOperationProject(
            project.getName(),
            project.getPath(),
            ((ProjectInternal) project).getIdentityPath().toString(),
            project.getProjectDir().getAbsolutePath(),
            project.getBuildFile().getAbsolutePath(),
            convert(project.getChildProjects().values()));
    }

    private Set<LoadProjectsBuildOperationType.Result.Project> convert(Iterable<Project> children) {
        ImmutableSortedSet.Builder<LoadProjectsBuildOperationType.Result.Project> builder = new ImmutableSortedSet.Builder<>(PROJECT_COMPARATOR);
        for (org.gradle.api.Project child : children) {
            builder.add(convert(child));
        }
        return builder.build();
    }

    private static class BuildStructureOperationResult implements LoadProjectsBuildOperationType.Result {
        private final Project rootProject;
        private final String buildPath;

        public BuildStructureOperationResult(Project rootProject, String buildPath) {
            this.rootProject = rootProject;
            this.buildPath = buildPath;
        }

        @Override
        public Project getRootProject() {
            return rootProject;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }
    }

    private static class BuildStructureOperationProject implements LoadProjectsBuildOperationType.Result.Project {
        private final String name;
        private final String path;
        private final String identityPath;
        private final String projectDir;
        private final String buildFile;
        private final Set<LoadProjectsBuildOperationType.Result.Project> children;

        public BuildStructureOperationProject(String name, String path, String identityPath, String projectDir, String buildFile, Set<LoadProjectsBuildOperationType.Result.Project> children) {
            this.name = name;
            this.path = path;
            this.identityPath = identityPath;
            this.projectDir = projectDir;
            this.buildFile = buildFile;
            this.children = children;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getIdentityPath() {
            return identityPath;
        }

        @Override
        public String getProjectDir() {
            return projectDir;
        }

        @Override
        public String getBuildFile() {
            return buildFile;
        }

        @Override
        public Set<LoadProjectsBuildOperationType.Result.Project> getChildren() {
            return children;
        }
    }

    private static final Comparator<LoadProjectsBuildOperationType.Result.Project> PROJECT_COMPARATOR =
        (o1, o2) -> o1.getName().compareTo(o2.getName());
}
