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

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.util.CollectionUtils;

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
                context.setResult(new OperationResult(convertDescriptors(settingsInternal.getRootProject()), settingsInternal.getGradle().getIdentityPath().absolutePath(":")));
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

    private ConfigureSettingsBuildOperationType.ProjectDescriptor convertDescriptors(org.gradle.api.initialization.ProjectDescriptor projectDescriptor) {
        return new ConfigureSettingsBuildOperationType.ProjectDescriptor(projectDescriptor.getName(),
            projectDescriptor.getPath(),
            projectDescriptor.getProjectDir().getAbsolutePath(),
            projectDescriptor.getBuildFile().getAbsolutePath(),
            convertDescriptors(projectDescriptor.getChildren()));
    }

    private Set<ConfigureSettingsBuildOperationType.ProjectDescriptor> convertDescriptors(Set<org.gradle.api.initialization.ProjectDescriptor> children) {
        return CollectionUtils.collect(children, new Transformer<ConfigureSettingsBuildOperationType.ProjectDescriptor, org.gradle.api.initialization.ProjectDescriptor>() {
            @Override
            public ConfigureSettingsBuildOperationType.ProjectDescriptor transform(org.gradle.api.initialization.ProjectDescriptor projectDescriptor) {
                return convertDescriptors(projectDescriptor);
            }
        });
    }

    private class OperationResult implements ConfigureSettingsBuildOperationType.Result {
        private final ConfigureSettingsBuildOperationType.ProjectDescriptor rootProject;
        private final String buildPath;

        public OperationResult(ConfigureSettingsBuildOperationType.ProjectDescriptor rootProject, String buildPath) {
            this.rootProject = rootProject;
            this.buildPath = buildPath;
        }

        @Override
        public ConfigureSettingsBuildOperationType.ProjectDescriptor getRootProject() {
            return rootProject;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }
    }
}
