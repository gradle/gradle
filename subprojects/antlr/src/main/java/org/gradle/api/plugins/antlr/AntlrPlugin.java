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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.antlr.internal.AntlrSourceVirtualDirectoryImpl;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_CONFIGURATION_NAME;

/**
 * A plugin for adding Antlr support to {@link JavaPlugin java projects}.
 */
public class AntlrPlugin implements Plugin<Project> {
    public static final String ANTLR_CONFIGURATION_NAME = "antlr";
    private final SourceDirectorySetFactory sourceDirectorySetFactory;

    @Inject
    public AntlrPlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
    }

    public void apply(final Project project) {
        project.getPluginManager().apply(JavaPlugin.class);

        // set up a configuration named 'antlr' for the user to specify the antlr libs to use in case
        // they want a specific version etc.
        final Configuration antlrConfiguration = project.getConfigurations().create(ANTLR_CONFIGURATION_NAME)
                .setVisible(false)
                .setDescription("The Antlr libraries to be used for this project.");

        antlrConfiguration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                dependencies.add(project.getDependencies().create("antlr:antlr:2.7.7@jar"));
            }
        });

        project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME).extendsFrom(antlrConfiguration);

        // Wire the antlr configuration into all antlr tasks
        project.getTasks().withType(AntlrTask.class, new Action<AntlrTask>() {
            public void execute(AntlrTask antlrTask) {
                antlrTask.getConventionMapping().map("antlrClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return project.getConfigurations().getByName(ANTLR_CONFIGURATION_NAME);
                    }
                });
            }
        });

        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(
                new Action<SourceSet>() {
                    public void execute(SourceSet sourceSet) {
                        // for each source set we will:
                        // 1) Add a new 'antlr' virtual directory mapping
                        final AntlrSourceVirtualDirectoryImpl antlrDirectoryDelegate
                                = new AntlrSourceVirtualDirectoryImpl(((DefaultSourceSet) sourceSet).getDisplayName(), sourceDirectorySetFactory);
                        new DslObject(sourceSet).getConvention().getPlugins().put(
                                AntlrSourceVirtualDirectory.NAME, antlrDirectoryDelegate);
                        final String srcDir = "src/"+ sourceSet.getName() +"/antlr";
                        antlrDirectoryDelegate.getAntlr().srcDir(srcDir);
                        sourceSet.getAllSource().source(antlrDirectoryDelegate.getAntlr());

                        // 2) create an AntlrTask for this sourceSet following the gradle
                        //    naming conventions via call to sourceSet.getTaskName()
                        final String taskName = sourceSet.getTaskName("generate", "GrammarSource");
                        AntlrTask antlrTask = project.getTasks().create(taskName, AntlrTask.class);
                        antlrTask.setDescription("Processes the " + sourceSet.getName() + " Antlr grammars.");

                        // 3) set up convention mapping for default sources (allows user to not have to specify)
                        antlrTask.setSource(antlrDirectoryDelegate.getAntlr());

                        // 4) Set up the Antlr output directory (adding to javac inputs!)
                        final String outputDirectoryName = project.getBuildDir() + "/generated-src/antlr/" + sourceSet.getName();
                        final File outputDirectory = new File(outputDirectoryName);
                        antlrTask.setOutputDirectory(outputDirectory);
                        sourceSet.getJava().srcDir(outputDirectory);

                        // 6) register fact that antlr should be run before compiling
                        project.getTasks().getByName(sourceSet.getCompileJavaTaskName()).dependsOn(taskName);
                    }
                });
    }
}
