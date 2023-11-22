/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.plugins.BuildConfigurationRule;
import org.gradle.api.internal.plugins.NaggingBasePluginConvention;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.plugins.internal.DefaultBasePluginExtension;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Describables;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.function.Supplier;

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle and some common convention properties.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html">Base plugin reference</a>
 */
public abstract class BasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = LifecycleBasePlugin.CLEAN_TASK_NAME;
    public static final String ASSEMBLE_TASK_NAME = LifecycleBasePlugin.ASSEMBLE_TASK_NAME;
    public static final String BUILD_GROUP = LifecycleBasePlugin.BUILD_GROUP;

    private static final String EXPLICIT_ASSEMBLE_PROPERTY_NAME = "org.gradle.preview.explicit-assemble";

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        BasePluginExtension baseExtension = project.getExtensions().create(BasePluginExtension.class, "base", DefaultBasePluginExtension.class, project);

        addConvention(project, baseExtension);
        configureExtension(project, baseExtension);
        configureBuildConfigurationRule(project);
        configureArchiveDefaults(project, baseExtension);

        // TODO: Why is this here?
        // This was originally added in 2008 here: https://github.com/gradle/gradle/commit/ca605c56c816a63025bbe62247d112358c17b98c
        // However, the commit message doesn't explain why it was added.
        // Project#getStatus claims the default value is `release`.
        ((ProjectInternal) project).getInternalStatus().convention("integration");

        RoleBasedConfigurationContainerInternal configurations = (RoleBasedConfigurationContainerInternal) project.getConfigurations();
        configureDefaultConfiguration(configurations);
        configureArchivesConfiguration(project, configurations);
    }


    @SuppressWarnings("deprecation")
    private void addConvention(Project project, BasePluginExtension baseExtension) {
        BasePluginConvention convention = project.getObjects().newInstance(org.gradle.api.plugins.internal.DefaultBasePluginConvention.class, baseExtension);
        DeprecationLogger.whileDisabled(() -> {
            project.getConvention().getPlugins().put("base", new NaggingBasePluginConvention(convention));
        });
    }

    private void configureExtension(Project project, BasePluginExtension extension) {
        extension.getArchivesName().convention(project.getName());
        extension.getLibsDirectory().convention(project.getLayout().getBuildDirectory().dir("libs"));
        extension.getDistsDirectory().convention(project.getLayout().getBuildDirectory().dir("distributions"));
    }

    private void configureArchiveDefaults(final Project project, final BasePluginExtension extension) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
            task.getDestinationDirectory().convention(extension.getDistsDirectory());
            task.getArchiveVersion().convention(
                project.provider(() -> project.getVersion() == Project.DEFAULT_VERSION ? null : project.getVersion().toString())
            );

            task.getArchiveBaseName().convention(extension.getArchivesName());
        });
    }

    private void configureBuildConfigurationRule(Project project) {
        project.getTasks().addRule(new BuildConfigurationRule(project.getConfigurations(), project.getTasks()));
    }

    private static void configureDefaultConfiguration(RoleBasedConfigurationContainerInternal configurations) {
        configurations.maybeCreateConsumableUnlocked(Dependency.DEFAULT_CONFIGURATION)
            .setDescription("Configuration for default artifacts.");
    }

    private static void configureArchivesConfiguration(Project project, RoleBasedConfigurationContainerInternal configurations) {
        @SuppressWarnings("deprecation")
        final Configuration archivesConfiguration = configurations.maybeCreateMigratingUnlocked(Dependency.ARCHIVES_CONFIGURATION, ConfigurationRolesForMigration.CONSUMABLE_TO_REMOVED)
            .setDescription("Configuration for archive artifacts.");

        @SuppressWarnings("deprecation")
        final org.gradle.api.internal.plugins.DefaultArtifactPublicationSet defaultArtifacts =
            project.getExtensions().create("defaultArtifacts", org.gradle.api.internal.plugins.DefaultArtifactPublicationSet.class, archivesConfiguration.getArtifacts());

        configurations.all(configuration -> {
            if (!configuration.equals(archivesConfiguration)) {
                configuration.getArtifacts().configureEach(artifact -> {
                    if (configuration.isVisible()) {
                        defaultArtifacts.addCandidateInternal(artifact, true);
                    }
                });
            }
        });

        linkArchiveArtifactsToAssembleTask(project, () -> {
            String advice = "Set the gradle property '" + EXPLICIT_ASSEMBLE_PROPERTY_NAME  + "=true' to opt into the new behavior and silence this warning. " +
                "To continue building this artifact when running 'assemble', manually define the task dependency with 'tasks.assemble.dependsOn(Object)'";

            PublishArtifactSet archiveArtifacts = archivesConfiguration.getAllArtifacts();
            for (PublishArtifact artifact : archiveArtifacts) {
                if (!defaultArtifacts.shouldWarn(artifact)) {
                    // Artifacts added by first-party plugins skip this warning.
                    // In 9.0 they will migrate to assemble.dependsOn()
                    continue;
                }
                boolean found = false;
                for (Configuration conf : configurations) {
                    if (conf.equals(archivesConfiguration)) {
                        continue;
                    }
                    if (conf.getArtifacts().contains(artifact)) {
                        found = true;
                        String message = String.format(
                            "%s for configuration '%s' is automatically built by the 'assemble' task. " +
                                "Building configuration artifacts automatically in this manner",
                            getArtifactDisplayName(artifact), conf.getName()
                        );
                        DeprecationLogger.deprecate(message)
                            .withAdvice(advice)
                            .startingWithGradle9("the 'assemble' task will no longer build this artifact automatically")
                            .withUpgradeGuideSection(8, "deprecated_archives_configuration")
                            .nagUser();
                    }
                }

                if (!found) {
                    String message = String.format(
                        "%s is automatically built by the 'assemble' task since it was added to the 'archives' configuration. " +
                            "Building 'archives' configuration artifacts automatically in this manner",
                        getArtifactDisplayName(artifact)
                    );
                    DeprecationLogger.deprecate(message)
                        .withAdvice(advice)
                        .startingWithGradle9("the 'assemble' task will no longer build this artifact automatically")
                        .withUpgradeGuideSection(8, "deprecated_archives_configuration")
                        .nagUser();
                }
            }

            return archiveArtifacts.getBuildDependencies();
        });
    }

    private static String getArtifactDisplayName(PublishArtifact artifact) {
        if (artifact instanceof ArchivePublishArtifact) {
            return ((ArchivePublishArtifact) artifact).getArchiveTask().toString();
        }
        return Describables.of("Artifact", artifact.getFile().getName()).toString();
    }

    private static void linkArchiveArtifactsToAssembleTask(Project project, Supplier<TaskDependency> assembleBuildDependencies) {
        if ("true".equals(project.findProperty(EXPLICIT_ASSEMBLE_PROPERTY_NAME))) {
            return;
        }

        project.getTasks().named(ASSEMBLE_TASK_NAME, task -> {
            task.dependsOn(new TaskDependencyContainer() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    context.add(assembleBuildDependencies.get());
                }
            });
        });
    }
}
