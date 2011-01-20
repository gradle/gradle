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
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;

import java.io.File;

/**
 * <p>A {@link org.gradle.api.Plugin} which extends the {@link org.gradle.api.plugins.JavaBasePlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 *
 * @author Hans Dockter
 */
public class GroovyBasePlugin implements Plugin<Project> {
    public static final String GROOVY_CONFIGURATION_NAME = "groovy";

    public void apply(Project project) {
        JavaBasePlugin javaBasePlugin = project.getPlugins().apply(JavaBasePlugin.class);

        project.getConfigurations().add(GROOVY_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("The groovy libraries to be used for this Groovy project.");

        configureCompileDefaults(project);
        configureSourceSetDefaults(project, javaBasePlugin);

        configureGroovydoc(project);
    }

    private void configureCompileDefaults(final Project project) {
        project.getTasks().withType(GroovyCompile.class, new Action<GroovyCompile>() {
            public void execute(GroovyCompile compile) {
                compile.getConventionMapping().map("groovyClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME).copy().setTransitive(true);
                    }
                });
            }
        });
    }

    private void configureSourceSetDefaults(final Project project, final JavaBasePlugin javaBasePlugin) {
        final ProjectInternal projectInternal = (ProjectInternal) project;
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            public void execute(SourceSet sourceSet) {
                final DefaultGroovySourceSet groovySourceSet = new DefaultGroovySourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), projectInternal.getFileResolver());
                ((DynamicObjectAware) sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet);

                groovySourceSet.getGroovy().srcDir(String.format("src/%s/groovy", sourceSet.getName()));
                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return groovySourceSet.getGroovy().contains(element.getFile());
                    }
                });
                sourceSet.getAllJava().add(groovySourceSet.getGroovy().matching(sourceSet.getJava().getFilter()));
                sourceSet.getAllSource().add(groovySourceSet.getGroovy());

                String compileTaskName = sourceSet.getCompileTaskName("groovy");
                GroovyCompile compile = project.getTasks().add(compileTaskName, GroovyCompile.class);
                javaBasePlugin.configureForSourceSet(sourceSet, compile);
                compile.dependsOn(sourceSet.getCompileJavaTaskName());
                compile.setDescription(String.format("Compiles the %s Groovy source.", sourceSet.getName()));
                compile.conventionMapping("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return groovySourceSet.getGroovy();
                    }
                });

                project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
            }
        });
    }

    private void configureGroovydoc(final Project project) {
        project.getTasks().withType(Groovydoc.class, new Action<Groovydoc>() {
            public void execute(Groovydoc groovydoc) {
                groovydoc.getConventionMapping().map("groovyClasspath", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME).copy().setTransitive(true);
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
    }

    private JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }
}