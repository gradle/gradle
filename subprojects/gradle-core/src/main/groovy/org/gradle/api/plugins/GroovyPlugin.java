/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.internal.DynamicObjectAware;
import static org.gradle.api.plugins.JavaPlugin.*;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 *
 * @author Hans Dockter
 */
public class GroovyPlugin implements Plugin {
    public static final String GROOVYDOC_TASK_NAME = "groovydoc";
    public static final String GROOVY_CONFIGURATION_NAME = "groovy";

    public void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        JavaPlugin javaPlugin = projectPluginsHandler.usePlugin(JavaPlugin.class, project);

        Configuration groovyConfiguration = project.getConfigurations().add(GROOVY_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("The groovy libraries to be used for this Groovy project.");
        project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME).extendsFrom(groovyConfiguration);

        configureCompileDefaults(project);
        configureSourceSetDefaults(project, javaPlugin);

        configureJavadoc(project);
        configureGroovydoc(project);
    }

    private void configureCompileDefaults(final Project project) {
        project.getTasks().withType(GroovyCompile.class).allTasks(new Action<GroovyCompile>() {
            public void execute(GroovyCompile compile) {
                compile.setGroovyClasspath(project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME));
                compile.getConventionMapping().map("groovySourceDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new ArrayList<File>(mainGroovy(convention).getGroovy().getSrcDirs());
                    }
                });
            }
        });
    }

    private void configureSourceSetDefaults(final Project project, final JavaPlugin javaPlugin) {
        final ProjectInternal projectInternal = (ProjectInternal) project;
        project.getConvention().getPlugin(JavaPluginConvention.class).getSource().allObjects(new Action<SourceSet>() {
            public void execute(SourceSet sourceSet) {
                final DefaultGroovySourceSet groovySourceSet = new DefaultGroovySourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), projectInternal.getFileResolver());
                ((DynamicObjectAware) sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet);
                groovySourceSet.getGroovy().srcDir(String.format("src/%s/groovy", sourceSet.getName()));
                sourceSet.getAllJava().add(groovySourceSet.getGroovy().matching(sourceSet.getJavaSourcePatterns()));

                TaskDependency javaCompileTaskDependency =  project.getTasks().getByName(sourceSet.getCompileTaskName()).getTaskDependencies();
                String compileTaskName = String.format("%sGroovy", sourceSet.getCompileTaskName());
                GroovyCompile compile = project.getTasks().add(compileTaskName, GroovyCompile.class);
                javaPlugin.configureForSourceSet(sourceSet, compile);
                compile.dependsOn(javaCompileTaskDependency);
                compile.dependsOn(sourceSet.getCompileTaskName());
                compile.setDescription(String.format("Compiles the %s Groovy source.", sourceSet.getName()));
                compile.conventionMapping("groovySourceDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new ArrayList<File>(groovySourceSet.getGroovy().getSrcDirs());
                    }
                });

                sourceSet.compiledBy(compileTaskName);
            }
        });
    }

    private void configureGroovydoc(final Project project) {
        project.getTasks().withType(Groovydoc.class).allTasks(new Action<Groovydoc>() {
            public void execute(Groovydoc groovydoc) {
                groovydoc.setGroovyClasspath(project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME));
                groovydoc.getConventionMapping().map("srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new ArrayList<File>(mainGroovy(convention).getGroovy().getSrcDirs());
                    }
                });
                groovydoc.getConventionMapping().map("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new File(java(convention).getDocsDir(), "groovydoc");
                    }
                });
            }
        });
        project.getTasks().add(GROOVYDOC_TASK_NAME, Groovydoc.class).setDescription("Generates the groovydoc for the source code.");
    }

    private void configureJavadoc(Project project) {
        Action<Javadoc> taskListener = new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.exclude("**/*.groovy");
                javadoc.conventionMapping("srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return GUtil.addLists(main(convention).getJava().getSrcDirs(),
                                mainGroovy(convention).getGroovy().getSrcDirs());
                    }
                });
            }
        };
        project.getTasks().withType(Javadoc.class).allTasks(taskListener);
    }

    private JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }

    private SourceSet main(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class).getSource().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
    }

    private GroovySourceSet mainGroovy(Convention convention) {
        return ((DynamicObjectAware) main(convention)).getConvention().getPlugin(GroovySourceSet.class);
    }

}
