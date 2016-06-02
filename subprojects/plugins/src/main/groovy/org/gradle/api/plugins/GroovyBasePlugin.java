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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GroovyRuntime;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Extends {@link org.gradle.api.plugins.JavaBasePlugin} to provide support for compiling and documenting Groovy
 * source files.
 */
public class GroovyBasePlugin implements Plugin<Project> {
    public static final String GROOVY_RUNTIME_EXTENSION_NAME = "groovyRuntime";

    private final SourceDirectorySetFactory sourceDirectorySetFactory;
    private final ModuleRegistry moduleRegistry;

    private Project project;
    private GroovyRuntime groovyRuntime;

    @Inject
    public GroovyBasePlugin(SourceDirectorySetFactory sourceDirectorySetFactory, ModuleRegistry moduleRegistry) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
        this.moduleRegistry = moduleRegistry;
    }

    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaBasePlugin.class);
        JavaBasePlugin javaBasePlugin = project.getPlugins().getPlugin(JavaBasePlugin.class);

        configureGroovyRuntimeExtension();
        configureCompileDefaults();
        configureSourceSetDefaults(javaBasePlugin);

        configureGroovydoc();
    }

    private void configureGroovyRuntimeExtension() {
        groovyRuntime = project.getExtensions().create(GROOVY_RUNTIME_EXTENSION_NAME, GroovyRuntime.class, project);
    }

    private void configureCompileDefaults() {
        project.getTasks().withType(GroovyCompile.class, new Action<GroovyCompile>() {
            public void execute(final GroovyCompile compile) {
                compile.getConventionMapping().map("groovyClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return groovyRuntime.inferGroovyClasspath(compile.getClasspath());
                    }
                });
            }
        });
    }

    private void configureSourceSetDefaults(final JavaBasePlugin javaBasePlugin) {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            public void execute(SourceSet sourceSet) {
                final DefaultGroovySourceSet groovySourceSet = new DefaultGroovySourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), sourceDirectorySetFactory);
                new DslObject(sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet);

                groovySourceSet.getGroovy().srcDir("src/" + sourceSet.getName() + "/groovy");
                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return groovySourceSet.getGroovy().contains(element.getFile());
                    }
                });
                sourceSet.getAllJava().source(groovySourceSet.getGroovy());
                sourceSet.getAllSource().source(groovySourceSet.getGroovy());

                String compileTaskName = sourceSet.getCompileTaskName("groovy");
                GroovyCompile compile = project.getTasks().create(compileTaskName, GroovyCompile.class);
                javaBasePlugin.configureForSourceSet(sourceSet, compile);
                compile.dependsOn(sourceSet.getCompileJavaTaskName());
                compile.setDescription("Compiles the " + sourceSet.getName() + " Groovy source.");
                compile.setSource(groovySourceSet.getGroovy());

                project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
            }
        });
    }

    private void configureGroovydoc() {
        project.getTasks().withType(Groovydoc.class, new Action<Groovydoc>() {
            public void execute(final Groovydoc groovydoc) {
                groovydoc.getConventionMapping().map("groovyClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        FileCollection groovyClasspath = groovyRuntime.inferGroovyClasspath(groovydoc.getClasspath());
                        // Jansi is required to log errors when generating Groovydoc
                        ConfigurableFileCollection jansi = project.files(moduleRegistry.getExternalModule("jansi").getImplementationClasspath().getAsFiles());
                        return groovyClasspath.plus(jansi);
                    }
                });
                groovydoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(java(project.getConvention()).getDocsDir(), "groovydoc");
                    }
                });
                groovydoc.getConventionMapping().map("docTitle", new Callable<Object>() {
                    public Object call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
                groovydoc.getConventionMapping().map("windowTitle", new Callable<Object>() {
                    public Object call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
            }
        });
    }

    private JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }
}
