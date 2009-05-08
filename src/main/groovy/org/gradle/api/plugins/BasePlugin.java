/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Rule;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishInstruction;
import org.gradle.api.tasks.Clean;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Upload;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.internal.IConventionAware;
import org.gradle.util.GUtil;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class BasePlugin implements Plugin {
    public static final String CLEAN_TASK_NAME = "clean";

    public void apply(final Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        project.getConvention().getPlugins().put("base", new BasePluginConvention(project, new HashMap()));

        configureBuildConfigurationRule(project);
        configureUploadRules(project);

        addClean(project);
    }

    private void addClean(final Project project) {
        project.getTasks().add(CLEAN_TASK_NAME, Clean.class).
                conventionMapping(GUtil.map(
                        "dir", new ConventionValue() {
                            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                                return project.getBuildDir();
                            }
                        })).setDescription("Deletes the build directory.");
    }

    private void configureBuildConfigurationRule(final Project project) {
        final String prefix = "build";
        project.getTasks().addRule(new Rule() {
            public String getDescription() {
                return String.format("Pattern: %s<ConfigurationName>: Builds the artifacts belonging to the configuration.", prefix);
            }

            public void apply(String taskName) {
                if (taskName.startsWith(prefix)) {
                    Configuration configuration = project.getConfigurations().findByName(taskName.substring(prefix.length()).toLowerCase());
                    if (configuration != null) {
                        project.getTasks().add(taskName)
                                .dependsOn(configuration.getBuildArtifacts())
                                .setDescription(String.format("Build the artifacts belonging to %s.", configuration));
                    }
                }
            }
        });
    }

    private void configureUploadRules(final Project project) {
        project.getTasks().addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>Internal: Upload the project artifacts of a configuration to the internal Gradle repository.";
            }

            public void apply(String taskName) {
                Set<Configuration> configurations = project.getConfigurations().getAll();
                for (Configuration configuration : configurations) {
                    if (taskName.equals(configuration.getUploadInternalTaskName())) {
                        Upload uploadInternal = createUploadTask(configuration.getUploadInternalTaskName(), configuration, project);
                        uploadInternal.getRepositories().add(project.getBuild().getInternalRepository());
                    }
                }
            }
        });

        project.getTasks().addRule(new Rule() {
            public String getDescription() {
                return "Pattern: upload<ConfigurationName>: Upload the project artifacts of a configuration to a public Gradle repository.";
            }

            public void apply(String taskName) {
                Set<Configuration> configurations = project.getConfigurations().getAll();
                for (Configuration configuration : configurations) {
                    if (taskName.equals(configuration.getUploadTaskName())) {
                        createUploadTask(configuration.getUploadTaskName(), configuration, project);
                    }
                }
            }
        });
    }

    private Upload createUploadTask(String name, final Configuration configuration, Project project) {
        Upload upload = project.getTasks().add(name, Upload.class);
        PublishInstruction publishInstruction = new PublishInstruction();
        publishInstruction.setIvyFileParentDir(project.getBuildDir());
        upload.setConfiguration(configuration);
        upload.setPublishInstruction(publishInstruction);
        upload.dependsOn(configuration.getBuildArtifacts());
        upload.setDescription(String.format("Uploads all artifacts belonging to %s.", configuration));
        return upload;
    }
}
