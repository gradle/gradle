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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * <p>
 * A {@link Plugin} with tasks which assemble a web application into a EAR file.
 * </p>
 */
public class EarPlugin implements Plugin<Project> {

    public static final String EAR_TASK_NAME = "ear";

    public static final String DEPLOY_CONFIGURATION_NAME = "deploy";
    public static final String EARLIB_CONFIGURATION_NAME = "earlib";
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    @Inject
    public EarPlugin(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(BasePlugin.class);

        final EarPluginConvention earPluginConvention = instantiator.newInstance(EarPluginConvention.class, fileResolver);
        project.getConvention().getPlugins().put("ear", earPluginConvention);
        earPluginConvention.setLibDirName("lib");
        earPluginConvention.setAppDirName("src/main/application");

        wireEarTaskConventions(project, earPluginConvention);
        configureConfigurations(project);

        PluginContainer plugins = project.getPlugins();

        setupEarTask(project, earPluginConvention);

        configureWithJavaPluginApplied(project, earPluginConvention, plugins);
        configureWithNoJavaPluginApplied(project, earPluginConvention);
    }

    private void configureWithNoJavaPluginApplied(final Project project, final EarPluginConvention earPluginConvention) {
        project.getTasks().withType(Ear.class, new Action<Ear>() {
            public void execute(final Ear task) {
                task.from(new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
                            return null;
                        } else {
                            return project.fileTree(earPluginConvention.getAppDirName());
                        }
                    }
                });
            }
        });
    }

    private void configureWithJavaPluginApplied(final Project project, final EarPluginConvention earPluginConvention, PluginContainer plugins) {
        plugins.withType(JavaPlugin.class, new Action<JavaPlugin>() {
            public void execute(JavaPlugin javaPlugin) {
                final JavaPluginConvention javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);

                SourceSet sourceSet = javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                sourceSet.getResources().srcDir(new Callable() {
                    public Object call() throws Exception {
                        return earPluginConvention.getAppDirName();
                    }
                });
                project.getTasks().withType(Ear.class, new Action<Ear>() {
                    public void execute(final Ear task) {
                        task.dependsOn(new Callable<FileCollection>() {
                            public FileCollection call() throws Exception {
                                return javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                                        .getRuntimeClasspath();
                            }
                        });
                        task.from(new Callable<FileCollection>() {
                            public FileCollection call() throws Exception {
                                return javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
                            }
                        });
                    }
                });
            }
        });
    }

    private void setupEarTask(final Project project, EarPluginConvention convention) {
        Ear ear = project.getTasks().create(EAR_TASK_NAME, Ear.class);
        ear.setDescription("Generates a ear archive with all the modules, the application descriptor and the libraries.");
        DeploymentDescriptor deploymentDescriptor = convention.getDeploymentDescriptor();
        if (deploymentDescriptor != null) {
            if (deploymentDescriptor.getDisplayName() == null) {
                deploymentDescriptor.setDisplayName(project.getName());
            }
            if (deploymentDescriptor.getDescription() == null) {
                deploymentDescriptor.setDescription(project.getDescription());
            }
        }
        ear.setGroup(BasePlugin.BUILD_GROUP);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(new ArchivePublishArtifact(ear));

        project.getTasks().withType(Ear.class, new Action<Ear>() {
            public void execute(Ear task) {
                task.getLib().from(new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        return project.getConfigurations().getByName(EARLIB_CONFIGURATION_NAME);
                    }
                });
                task.from(new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        // add the module configuration's files
                        return project.getConfigurations().getByName(DEPLOY_CONFIGURATION_NAME);
                    }
                });
            }
        });
    }

    private void wireEarTaskConventions(Project project, final EarPluginConvention earConvention) {
        project.getTasks().withType(Ear.class, new Action<Ear>() {
            public void execute(Ear task) {
                task.getConventionMapping().map("libDirName", new Callable<String>() {
                    public String call() throws Exception { return earConvention.getLibDirName(); }
                });
                task.getConventionMapping().map("deploymentDescriptor", new Callable<DeploymentDescriptor>() {
                    public DeploymentDescriptor call() throws Exception { return earConvention.getDeploymentDescriptor(); }
                });
            }
        });
    }

    private void configureConfigurations(final Project project) {

        ConfigurationContainer configurations = project.getConfigurations();
        Configuration moduleConfiguration = configurations.create(DEPLOY_CONFIGURATION_NAME).setVisible(false)
                .setTransitive(false).setDescription("Classpath for deployable modules, not transitive.");
        Configuration earlibConfiguration = configurations.create(EARLIB_CONFIGURATION_NAME).setVisible(false)
                .setDescription("Classpath for module dependencies.");

        configurations.getByName(Dependency.DEFAULT_CONFIGURATION)
                .extendsFrom(moduleConfiguration, earlibConfiguration);
    }
}
