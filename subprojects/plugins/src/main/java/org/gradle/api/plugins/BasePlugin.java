/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublication;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.plugins.BuildConfigurationRule;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.plugins.UploadRule;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.internal.DefaultBasePluginConvention;
import org.gradle.api.tasks.Upload;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.Describables;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * <p>A  {@link org.gradle.api.Plugin}  which defines a basic project lifecycle and some common convention properties.</p>
 */
public class BasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = LifecycleBasePlugin.CLEAN_TASK_NAME;
    public static final String ASSEMBLE_TASK_NAME = LifecycleBasePlugin.ASSEMBLE_TASK_NAME;
    public static final String BUILD_GROUP = LifecycleBasePlugin.BUILD_GROUP;

    public static final String UPLOAD_ARCHIVES_TASK_NAME = "uploadArchives";
    public static final String UPLOAD_GROUP = "upload";

    private final ProjectPublicationRegistry publicationRegistry;
    private final ProjectConfigurationActionContainer configurationActionContainer;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    @Inject
    public BasePlugin(ProjectPublicationRegistry publicationRegistry, ProjectConfigurationActionContainer configurationActionContainer, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.publicationRegistry = publicationRegistry;
        this.configurationActionContainer = configurationActionContainer;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        BasePluginConvention convention = new DefaultBasePluginConvention(project);
        project.getConvention().getPlugins().put("base", convention);

        configureBuildConfigurationRule(project);
        configureUploadRules(project);
        configureUploadArchivesTask();
        configureArchiveDefaults(project, convention);
        configureConfigurations(project);
        configureAssemble((ProjectInternal) project);
    }

    private void configureArchiveDefaults(final Project project, final BasePluginConvention pluginConvention) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
            if (task instanceof Jar) {
                task.getDestinationDirectory().convention(pluginConvention.getLibsDirectory());
            } else {
                task.getDestinationDirectory().convention(pluginConvention.getDistsDirectory());
            }

            task.getArchiveVersion().convention(
                project.provider(() -> project.getVersion() == Project.DEFAULT_VERSION ? null : project.getVersion().toString())
            );

            task.getArchiveBaseName().convention(
                project.provider(() -> pluginConvention.getArchivesBaseName())
            );
        });
    }

    private void configureBuildConfigurationRule(Project project) {
        project.getTasks().addRule(new BuildConfigurationRule(project.getConfigurations(), project.getTasks()));
    }

    private void configureUploadRules(final Project project) {
        project.getTasks().addRule(new UploadRule(project));
    }

    private void configureUploadArchivesTask() {
        configurationActionContainer.add(project -> {
            Upload uploadArchives = project.getTasks().withType(Upload.class).findByName(UPLOAD_ARCHIVES_TASK_NAME);
            if (uploadArchives == null) {
                return;
            }
            AtomicBoolean usesMaven = new AtomicBoolean();
            project.getPluginManager().withPlugin("maven", p -> usesMaven.set(true));
            uploadArchives.doFirst(action(task -> DeprecationLogger
                .deprecateTask(UPLOAD_ARCHIVES_TASK_NAME)
                .withAdvice("Use the " + (usesMaven.get() ? "'maven-publish'" : "'ivy-publish'") + " plugin instead.")
                .willBeRemovedInGradle7()
                .withUpgradeGuideSection(5, "legacy_publication_system_is_deprecated_and_replaced_with_the_publish_plugins")
                .nagUser()
            ));
            boolean hasIvyRepo = !uploadArchives.getRepositories().withType(IvyArtifactRepository.class).isEmpty();
            if (!hasIvyRepo) {
                return;
            } // Maven repos are handled by MavenPlugin

            ConfigurationInternal configuration = (ConfigurationInternal) uploadArchives.getConfiguration();
            Module module = configuration.getModule();
            ModuleVersionIdentifier publicationId = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
            publicationRegistry.registerPublication(project, new DefaultProjectPublication(Describables.of("Ivy publication"), publicationId, true));
        });
    }

    private void configureConfigurations(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        project.setStatus("integration");

        final DeprecatableConfiguration archivesConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(Dependency.ARCHIVES_CONFIGURATION).
            setDescription("Configuration for archive artifacts.");

        final DeprecatableConfiguration defaultConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(Dependency.DEFAULT_CONFIGURATION).
            setDescription("Configuration for default artifacts.");

        final DefaultArtifactPublicationSet defaultArtifacts = project.getExtensions().create(
            "defaultArtifacts", DefaultArtifactPublicationSet.class, archivesConfiguration.getArtifacts()
        );

        archivesConfiguration.deprecateForResolution(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        defaultConfiguration.deprecateForResolution(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        archivesConfiguration.deprecateForDeclaration(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.API_CONFIGURATION_NAME);
        defaultConfiguration.deprecateForDeclaration(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.API_CONFIGURATION_NAME);

        configurations.all(configuration -> {
            if (!configuration.equals(archivesConfiguration)) {
                configuration.getArtifacts().configureEach(artifact -> {
                    if (configuration.isVisible()) {
                        defaultArtifacts.addCandidate(artifact);
                    }
                });
            }
        });
    }

    private void configureAssemble(final ProjectInternal project) {
        project.getTasks().named(ASSEMBLE_TASK_NAME, task -> {
            task.dependsOn(task.getProject().getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).getAllArtifacts().getBuildDependencies());
        });
    }
}
