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

package org.gradle.api.plugins.antlr;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin for adding Antlr support to {@link JavaPlugin java projects}.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/antlr_plugin.html">ANTLR plugin reference</a>
 */
public class AntlrPlugin implements Plugin<Project> {
    public static final String ANTLR_CONFIGURATION_NAME = "antlr";
    private final ObjectFactory objectFactory;

    @Inject
    public AntlrPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);

        // set up a configuration named 'antlr' for the user to specify the antlr libs to use in case
        // they want a specific version etc.
        final Configuration antlrConfiguration = project.getConfigurations().create(ANTLR_CONFIGURATION_NAME)
            .setVisible(false)
            .setDescription("The Antlr libraries to be used for this project.");
        ((DeprecatableConfiguration) antlrConfiguration).deprecateForConsumption(deprecation -> deprecation.willBecomeAnErrorInGradle8()
            .withUpgradeGuideSection(7, "plugin_configuration_consumption"));

        antlrConfiguration.defaultDependencies(dependencies -> dependencies.add(project.getDependencies().create("antlr:antlr:2.7.7@jar")));

        Configuration apiConfiguration = project.getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME);
        apiConfiguration.extendsFrom(antlrConfiguration);

        // Wire the antlr configuration into all antlr tasks
        project.getTasks().withType(AntlrTask.class).configureEach(antlrTask -> antlrTask.getConventionMapping().map("antlrClasspath", () -> project.getConfigurations().getByName(ANTLR_CONFIGURATION_NAME)));

        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().all(
            new Action<SourceSet>() {
                @Override
                public void execute(final SourceSet sourceSet) {
                    // for each source set we will:
                    // 1) Add a new 'antlr' virtual directory mapping
                    org.gradle.api.plugins.antlr.internal.AntlrSourceVirtualDirectoryImpl antlrDirectoryDelegate
                        = new org.gradle.api.plugins.antlr.internal.AntlrSourceVirtualDirectoryImpl(((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
                    new DslObject(sourceSet).getConvention().getPlugins().put(
                        AntlrSourceVirtualDirectory.NAME, antlrDirectoryDelegate);
                    sourceSet.getExtensions().add(AntlrSourceDirectorySet.class, AntlrSourceVirtualDirectory.NAME, antlrDirectoryDelegate.getAntlr());
                    final String srcDir = "src/" + sourceSet.getName() + "/antlr";
                    antlrDirectoryDelegate.getAntlr().srcDir(srcDir);
                    sourceSet.getAllSource().source(antlrDirectoryDelegate.getAntlr());

                    // 2) create an AntlrTask for this sourceSet following the gradle
                    //    naming conventions via call to sourceSet.getTaskName()
                    final String taskName = sourceSet.getTaskName("generate", "GrammarSource");

                    // 3) Set up the Antlr output directory (adding to javac inputs!)
                    final String outputDirectoryName = project.getBuildDir() + "/generated-src/antlr/" + sourceSet.getName();
                    final File outputDirectory = new File(outputDirectoryName);
                    sourceSet.getJava().srcDir(outputDirectory);

                    project.getTasks().register(taskName, AntlrTask.class, new Action<AntlrTask>() {
                        @Override
                        public void execute(AntlrTask antlrTask) {
                            antlrTask.setDescription("Processes the " + sourceSet.getName() + " Antlr grammars.");
                            // 4) set up convention mapping for default sources (allows user to not have to specify)
                            antlrTask.setSource(antlrDirectoryDelegate.getAntlr());
                            antlrTask.setOutputDirectory(outputDirectory);
                        }
                    });

                    // 5) register fact that antlr should be run before compiling
                    project.getTasks().named(sourceSet.getCompileJavaTaskName(), new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.dependsOn(taskName);
                        }
                    });
                }
            });
    }
}
