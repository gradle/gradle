/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ear;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * <p>
 * A {@link Plugin} with tasks which assemble a web application into a EAR file.
 * </p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/ear_plugin.html">EAR plugin reference</a>
 */
@SuppressWarnings("deprecation")
public abstract class EarPlugin implements Plugin<Project> {

    public static final String EAR_TASK_NAME = "ear";

    public static final String DEPLOY_CONFIGURATION_NAME = "deploy";
    public static final String EARLIB_CONFIGURATION_NAME = "earlib";

    static final String DEFAULT_LIB_DIR_NAME = "lib";

    private final ObjectFactory objectFactory;
    private final JvmPluginServices jvmPluginServices;
    private final TaskDependencyFactory taskDependencyFactory;

    /**
     * Injects an {@link ObjectFactory}
     *
     * @since 4.2
     */
    @Inject
    public EarPlugin(ObjectFactory objectFactory, JvmPluginServices jvmPluginServices, TaskDependencyFactory taskDependencyFactory) {
        this.objectFactory = objectFactory;
        this.jvmPluginServices = jvmPluginServices;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        EarPluginConvention earPluginConvention = objectFactory.newInstance(org.gradle.plugins.ear.internal.DefaultEarPluginConvention.class);
        DeprecationLogger.whileDisabled(() -> {
            project.getConvention().getPlugins().put("ear", earPluginConvention);
            earPluginConvention.setLibDirName(DEFAULT_LIB_DIR_NAME);
            earPluginConvention.setAppDirName("src/main/application");
        });

        configureConfigurations((ProjectInternal) project);

        PluginContainer plugins = project.getPlugins();
        setupEarTask(project, earPluginConvention, plugins);
        wireEarTaskConventions(project, earPluginConvention);
        wireEarTaskConventionsWithJavaPluginApplied(project, plugins);
    }

    private void wireEarTaskConventionsWithJavaPluginApplied(final Project project, PluginContainer plugins) {
        plugins.withType(JavaPlugin.class, javaPlugin -> {
            final JvmFeatureInternal mainFeature = JavaPluginHelper.getJavaComponent(project).getMainFeature();
            project.getTasks().withType(Ear.class).configureEach(task -> {
                task.dependsOn((Callable<FileCollection>) () ->
                    mainFeature.getSourceSet().getRuntimeClasspath()
                );
                task.from((Callable<FileCollection>) () ->
                    mainFeature.getSourceSet().getOutput()
                );
            });
        });
    }

    private void setupEarTask(final Project project, EarPluginConvention convention, PluginContainer plugins) {
        TaskProvider<Ear> ear = project.getTasks().register(EAR_TASK_NAME, Ear.class, task -> {
            task.setDescription("Generates a ear archive with all the modules, the application descriptor and the libraries.");
            task.setGroup(BasePlugin.BUILD_GROUP);
            DeprecationLogger.whileDisabled(() -> task.getGenerateDeploymentDescriptor().convention(convention.getGenerateDeploymentDescriptor()));

            plugins.withType(JavaPlugin.class, javaPlugin -> {
                final JvmSoftwareComponentInternal component = JavaPluginHelper.getJavaComponent(project);
                component.getMainFeature().getSourceSet().getResources().srcDir(task.getAppDirectory());
            });
        });

        DeploymentDescriptor deploymentDescriptor =  DeprecationLogger.whileDisabled(() -> convention.getDeploymentDescriptor());
        if (deploymentDescriptor != null) {
            deploymentDescriptor.getDisplayName().convention(project.getName());
            deploymentDescriptor.getDescription().convention(project.getDescription());
        }
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(new LazyPublishArtifact(ear, ((ProjectInternal) project).getFileResolver(), taskDependencyFactory));
    }

    private void wireEarTaskConventions(Project project, final EarPluginConvention earConvention) {
        project.getTasks().withType(Ear.class).configureEach(task -> {
            task.getAppDirectory().convention(project.provider(() -> project.getLayout().getProjectDirectory().dir(DeprecationLogger.whileDisabled(() -> earConvention.getAppDirName()))));
            task.getConventionMapping().map("libDirName", new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return DeprecationLogger.whileDisabled(() -> earConvention.getLibDirName());
                }
            });
            task.getConventionMapping().map("deploymentDescriptor", new Callable<DeploymentDescriptor>() {
                @Override
                public DeploymentDescriptor call() throws Exception {
                    return DeprecationLogger.whileDisabled(() -> earConvention.getDeploymentDescriptor());
                }
            });

            task.from((Callable<FileCollection>) () -> {
                if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
                    return null;
                } else {
                    return task.getAppDirectory().getAsFileTree();
                }
            });

            task.getLib().from((Callable<FileCollection>) () -> {
                // Ensure that deploy jars are not also added into lib folder.
                // Allows the user to get transitive dependencies for a bean artifact by adding it to both earlib and deploy but only having the file once in the ear.
                return project.getConfigurations().getByName(EARLIB_CONFIGURATION_NAME)
                    .minus(project.getConfigurations().getByName(DEPLOY_CONFIGURATION_NAME));
            });
            task.from((Callable<FileCollection>) () -> {
                // add the module configuration's files
                return project.getConfigurations().getByName(DEPLOY_CONFIGURATION_NAME);
            });
        });
    }

    private void configureConfigurations(final ProjectInternal project) {
        RoleBasedConfigurationContainerInternal configurations = project.getConfigurations();

        // Once these configurations become non-consumable, we can use
        // 'jvmPluginServices.configureAsRuntimeClasspath()' to configure the configurations.
        Configuration deployConfiguration = configurations.migratingUnlocked(DEPLOY_CONFIGURATION_NAME, ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE);
        deployConfiguration.setVisible(false);
        deployConfiguration.setTransitive(false);
        deployConfiguration.setDescription("Classpath for deployable modules, not transitive.");
        jvmPluginServices.configureAttributes(deployConfiguration, details -> details.library().runtimeUsage().withExternalDependencies());

        Configuration earlibConfiguration = configurations.migratingUnlocked(EARLIB_CONFIGURATION_NAME, ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE);
        earlibConfiguration.setVisible(false);
        earlibConfiguration.setDescription("Classpath for module dependencies.");
        jvmPluginServices.configureAttributes(earlibConfiguration, details -> details.library().runtimeUsage().withExternalDependencies());

        configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
            .extendsFrom(deployConfiguration, earlibConfiguration);
    }
}
