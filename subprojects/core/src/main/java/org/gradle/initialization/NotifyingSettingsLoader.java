/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import java.util.Comparator;
import java.util.Set;

public class NotifyingSettingsLoader implements SettingsLoader {
    private final SettingsLoader settingsLoader;
    private final BuildLoader buildLoader;
    private final BuildOperationExecutor buildOperationExecutor;

    public NotifyingSettingsLoader(SettingsLoader settingsLoader, BuildLoader buildLoader, BuildOperationExecutor buildOperationExecutor) {
        this.settingsLoader = settingsLoader;
        this.buildLoader = buildLoader;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public SettingsInternal findAndLoadSettings(final GradleInternal gradle) {
        final SettingsInternal settings = settingsLoader.findAndLoadSettings(gradle);
        gradle.getBuildListenerBroadcaster().settingsEvaluated(settings);
        try {
            buildOperationExecutor.call(new CallableBuildOperation<Void>() {
                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Loading Build").
                        progressDisplayName("Loading Build").
                        details(new LoadingBuildBuildOperationType.Details(){
                        });
                }

                @Override
                public Void call(BuildOperationContext context) {
                    buildLoader.load(settings.getRootProject(), settings.getDefaultProject(), gradle, settings.getRootClassLoaderScope());
                    Path buildPath = settings.getGradle().getIdentityPath();
                    context.setResult(new NotifyingSettingsLoader.OperationResult(convertDescriptors(gradle.getRootProject()), buildPath.toString()));
                    return null;
                }
            });
        } finally {
            gradle.getBuildListenerBroadcaster().projectsLoaded(gradle);
        }
        return settings;
    }

    private LoadingBuildBuildOperationType.Result.ProjectDescription convertDescriptors(Project project) {
        return new NotifyingSettingsLoader.DefaultProjectDescription(project.getName(),
            project.getPath(),
            ((ProjectInternal)project).getIdentityPath().toString(),
            project.getProjectDir().getAbsolutePath(),
            project.getBuildFile().getAbsolutePath(),
            ImmutableSortedSet.copyOf(PROJECT_DESCRIPTION_COMPARATOR, convertDescriptors(project.getSubprojects())));
    }

    private Set<LoadingBuildBuildOperationType.Result.ProjectDescription> convertDescriptors(Set<Project> children) {
        return CollectionUtils.collect(children, new Transformer<LoadingBuildBuildOperationType.Result.ProjectDescription, Project>() {
            @Override
            public LoadingBuildBuildOperationType.Result.ProjectDescription transform(Project project) {
                return convertDescriptors(project);
            }
        });
    }

    private class OperationResult implements LoadingBuildBuildOperationType.Result {
        private final LoadingBuildBuildOperationType.Result.ProjectDescription rootProject;
        private final String buildPath;

        public OperationResult(LoadingBuildBuildOperationType.Result.ProjectDescription rootProject, String buildPath) {
            this.rootProject = rootProject;
            this.buildPath = buildPath;
        }

        @Override
        public LoadingBuildBuildOperationType.Result.ProjectDescription getRootProject() {
            return rootProject;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }
    }

    private class DefaultProjectDescription implements LoadingBuildBuildOperationType.Result.ProjectDescription {
        final String name;
        final String path;
        private final String identityPath;
        final String projectDir;
        final String buildFile;
        final ImmutableSortedSet<LoadingBuildBuildOperationType.Result.ProjectDescription> children;

        public DefaultProjectDescription(String name, String path, String identityPath, String projectDir, String buildFile, ImmutableSortedSet<LoadingBuildBuildOperationType.Result.ProjectDescription> children){
            this.name = name;
            this.path = path;
            this.identityPath = identityPath;
            this.projectDir = projectDir;
            this.buildFile = buildFile;
            this.children = children;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        @Override
        public String getIdentityPath() {
            return identityPath;
        }

        public String getProjectDir() {
            return projectDir;
        }

        public String getBuildFile() {
            return buildFile;
        }

        public Set<LoadingBuildBuildOperationType.Result.ProjectDescription> getChildren() {
            return children;
        }
    }

    private static final Comparator<LoadingBuildBuildOperationType.Result.ProjectDescription> PROJECT_DESCRIPTION_COMPARATOR = new Comparator<LoadingBuildBuildOperationType.Result.ProjectDescription>() {
        @Override
        public int compare(LoadingBuildBuildOperationType.Result.ProjectDescription o1, LoadingBuildBuildOperationType.Result.ProjectDescription o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

}
