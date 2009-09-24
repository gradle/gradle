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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import static org.gradle.api.plugins.JavaPlugin.*;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;

import java.io.File;

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

        configureGroovydoc(project);
    }

    private void configureCompileDefaults(final Project project) {
        project.getTasks().withType(GroovyCompile.class).allTasks(new Action<GroovyCompile>() {
            public void execute(GroovyCompile compile) {
                compile.getConventionMapping().map("groovyClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME).copy().setTransitive(true);
                    }
                });
                compile.getConventionMapping().map("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return mainGroovy(convention).getGroovy();
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
                sourceSet.getResources().getFilter().exclude("**/*.groovy");
                sourceSet.getAllJava().add(groovySourceSet.getGroovy().matching(sourceSet.getJava().getFilter()));
                sourceSet.getAllSource().add(groovySourceSet.getGroovy());

                String compileTaskName = String.format("%sGroovy", sourceSet.getCompileTaskName());
                GroovyCompile compile = project.getTasks().add(compileTaskName, GroovyCompile.class);
                javaPlugin.configureForSourceSet(sourceSet, compile);
                compile.dependsOn(sourceSet.getCompileJavaTaskName());
                compile.setDescription(String.format("Compiles the %s Groovy source.", sourceSet.getName()));
                compile.conventionMapping("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return groovySourceSet.getGroovy();
                    }
                });

                project.getTasks().getByName(sourceSet.getCompileTaskName()).dependsOn(compileTaskName);
            }
        });
    }

    private void configureGroovydoc(final Project project) {
        project.getTasks().withType(Groovydoc.class).allTasks(new Action<Groovydoc>() {
            public void execute(Groovydoc groovydoc) {
                groovydoc.getConventionMapping().map("groovyClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME).copy().setTransitive(true);
                    }
                });
                groovydoc.getConventionMapping().map("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return mainGroovy(convention).getGroovy();
                    }
                });
                groovydoc.getConventionMapping().map("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new File(java(convention).getDocsDir(), "groovydoc");
                    }
                });
                groovydoc.getConventionMapping().map("docTitle", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(ReportingBasePluginConvention.class).getApiDocTitle();
                    }
                });
                groovydoc.getConventionMapping().map("windowTitle", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(ReportingBasePluginConvention.class).getApiDocTitle();
                    }
                });
            }
        });
        project.getTasks().add(GROOVYDOC_TASK_NAME, Groovydoc.class).setDescription("Generates the groovydoc for the source code.");
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
