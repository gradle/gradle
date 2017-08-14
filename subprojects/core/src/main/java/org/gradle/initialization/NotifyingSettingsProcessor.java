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
import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import java.util.Comparator;
import java.util.Set;

public class NotifyingSettingsProcessor implements SettingsProcessor {
    private final SettingsProcessor settingsProcessor;
    private final BuildOperationExecutor buildOperationExecutor;

    public NotifyingSettingsProcessor(SettingsProcessor settingsProcessor, BuildOperationExecutor buildOperationExecutor) {
        this.settingsProcessor = settingsProcessor;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public SettingsInternal process(final GradleInternal gradle, final SettingsLocation settingsLocation, final ClassLoaderScope buildRootClassLoaderScope, final StartParameter startParameter) {
        return buildOperationExecutor.call(new CallableBuildOperation<SettingsInternal>() {
            @Override
            public SettingsInternal call(BuildOperationContext context) {
                SettingsInternal settingsInternal = settingsProcessor.process(gradle, settingsLocation, buildRootClassLoaderScope, startParameter);
                Path buildPath = settingsInternal.getGradle().getIdentityPath();
                context.setResult(new OperationResult(convertDescriptors(settingsInternal.getRootProject(), buildPath), buildPath.absolutePath(":")));
                return settingsInternal;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Configure settings").
                    progressDisplayName("settings").
                    details(new ConfigureSettingsBuildOperationType.Details(){
                        @Override
                        public String getSettingsDir() {
                            return settingsLocation.getSettingsDir().getAbsolutePath();
                        }

                        @Override
                        public String getSettingsFile() {
                            return settingsLocation.getSettingsScriptSource().getFileName();
                        }
                    });
            }
        });
    }

    private ConfigureSettingsBuildOperationType.Result.ProjectDescription convertDescriptors(ProjectDescriptor projectDescription, Path path) {
        return new DefaultProjectDescription(projectDescription.getName(),
            projectDescription.getPath(),
            path.getPath(),
            projectDescription.getProjectDir().getAbsolutePath(),
            projectDescription.getBuildFile().getAbsolutePath(),
            ImmutableSortedSet.copyOf(PROJECT_DESCRIPTION_COMPARATOR, convertDescriptors(projectDescription.getChildren(), path)));
    }

    private Set<ConfigureSettingsBuildOperationType.Result.ProjectDescription> convertDescriptors(Set<ProjectDescriptor> children, final Path parentPath) {
        return CollectionUtils.collect(children, new Transformer<ConfigureSettingsBuildOperationType.Result.ProjectDescription, org.gradle.api.initialization.ProjectDescriptor>() {
            @Override
            public ConfigureSettingsBuildOperationType.Result.ProjectDescription transform(org.gradle.api.initialization.ProjectDescriptor projectDescriptor) {
                return convertDescriptors(projectDescriptor, parentPath.child(projectDescriptor.getName()));
            }
        });
    }

    private class OperationResult implements ConfigureSettingsBuildOperationType.Result {
        private final ConfigureSettingsBuildOperationType.Result.ProjectDescription rootProject;
        private final String buildPath;

        public OperationResult(ConfigureSettingsBuildOperationType.Result.ProjectDescription rootProject, String buildPath) {
            this.rootProject = rootProject;
            this.buildPath = buildPath;
        }

        @Override
        public ConfigureSettingsBuildOperationType.Result.ProjectDescription getRootProject() {
            return rootProject;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }
    }

    private class DefaultProjectDescription implements ConfigureSettingsBuildOperationType.Result.ProjectDescription {
        final String name;
        final String path;
        private final String identityPath;
        final String projectDir;
        final String buildFile;
        final ImmutableSortedSet<ConfigureSettingsBuildOperationType.Result.ProjectDescription> children;

        public DefaultProjectDescription(String name, String path, String identityPath, String projectDir, String buildFile, ImmutableSortedSet<ConfigureSettingsBuildOperationType.Result.ProjectDescription> children){
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

        public Set<ConfigureSettingsBuildOperationType.Result.ProjectDescription> getChildren() {
            return children;
        }
    }


    private static final Comparator<ConfigureSettingsBuildOperationType.Result.ProjectDescription> PROJECT_DESCRIPTION_COMPARATOR = new Comparator<ConfigureSettingsBuildOperationType.Result.ProjectDescription>() {
        @Override
        public int compare(ConfigureSettingsBuildOperationType.Result.ProjectDescription o1, ConfigureSettingsBuildOperationType.Result.ProjectDescription o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };


}
