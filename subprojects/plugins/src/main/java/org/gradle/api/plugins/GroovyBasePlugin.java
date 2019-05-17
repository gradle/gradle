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
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.JavaPluginsHelper;
import org.gradle.api.plugins.internal.SourceSetUtil;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GroovyRuntime;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.CompileOptions;
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

    private final ObjectFactory objectFactory;
    private final ModuleRegistry moduleRegistry;

    private Project project;
    private GroovyRuntime groovyRuntime;

    @Inject
    public GroovyBasePlugin(ObjectFactory objectFactory, ModuleRegistry moduleRegistry) {
        this.objectFactory = objectFactory;
        this.moduleRegistry = moduleRegistry;
    }

    @Override
    public void apply(Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaBasePlugin.class);

        configureGroovyRuntimeExtension();
        configureCompileDefaults();
        configureSourceSetDefaults();

        configureGroovydoc();
    }

    private void configureGroovyRuntimeExtension() {
        groovyRuntime = project.getExtensions().create(GROOVY_RUNTIME_EXTENSION_NAME, GroovyRuntime.class, project);
    }

    private void configureCompileDefaults() {
        project.getTasks().withType(GroovyCompile.class).configureEach(new Action<GroovyCompile>() {
            @Override
            public void execute(final GroovyCompile compile) {
                compile.getConventionMapping().map("groovyClasspath", new Callable<Object>() {
                    @Override
                    public Object call() {
                        return groovyRuntime.inferGroovyClasspath(compile.getClasspath());
                    }
                });
            }
        });
    }

    private void configureSourceSetDefaults() {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                final DefaultGroovySourceSet groovySourceSet = new DefaultGroovySourceSet("groovy", ((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
                new DslObject(sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet);

                groovySourceSet.getGroovy().srcDir("src/" + sourceSet.getName() + "/groovy");
                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    @Override
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return groovySourceSet.getGroovy().contains(element.getFile());
                    }
                });
                sourceSet.getAllJava().source(groovySourceSet.getGroovy());
                sourceSet.getAllSource().source(groovySourceSet.getGroovy());

                final Provider<GroovyCompile> compileTask = project.getTasks().register(sourceSet.getCompileTaskName("groovy"), GroovyCompile.class, new Action<GroovyCompile>() {
                    @Override
                    public void execute(final GroovyCompile compile) {
                        SourceSetUtil.configureForSourceSet(sourceSet, groovySourceSet.getGroovy(), compile, compile.getOptions(), project);
                        compile.dependsOn(sourceSet.getCompileJavaTaskName());
                        compile.setDescription("Compiles the " + sourceSet.getName() + " Groovy source.");
                        compile.setSource(groovySourceSet.getGroovy());
                    }
                });
                SourceSetUtil.configureOutputDirectoryForSourceSet(sourceSet, groovySourceSet.getGroovy(), project, compileTask, compileTask.map(new Transformer<CompileOptions, GroovyCompile>() {
                    @Override
                    public CompileOptions transform(GroovyCompile groovyCompile) {
                        return groovyCompile.getOptions();
                    }
                }));

                if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    // The user has chosen to use the java-library plugin;
                    // add a classes variant for groovy's main sourceSet compilation,
                    // so default-configuration project dependencies will see groovy's output.
                    project.getPluginManager().withPlugin("java-library", new Action<AppliedPlugin>() {
                        @Override
                        public void execute(AppliedPlugin plugin) {
                            Configuration apiElements = project.getConfigurations().getByName(sourceSet.getApiElementsConfigurationName());
                            JavaPluginsHelper.registerClassesDirVariant(compileTask, project.getObjects(), apiElements);
                        }
                    });
                }

                // TODO: `classes` should be a little more tied to the classesDirs for a SourceSet so every plugin
                // doesn't need to do this.
                project.getTasks().named(sourceSet.getClassesTaskName(), new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        task.dependsOn(compileTask);
                    }
                });
            }
        });
    }

    private void configureGroovydoc() {
        project.getTasks().withType(Groovydoc.class).configureEach(new Action<Groovydoc>() {
            @Override
            public void execute(final Groovydoc groovydoc) {
                groovydoc.getConventionMapping().map("groovyClasspath", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        FileCollection groovyClasspath = groovyRuntime.inferGroovyClasspath(groovydoc.getClasspath());
                        // Jansi is required to log errors when generating Groovydoc
                        ConfigurableFileCollection jansi = project.getObjects().fileCollection().from(moduleRegistry.getExternalModule("jansi").getImplementationClasspath().getAsFiles());
                        return groovyClasspath.plus(jansi);
                    }
                });
                groovydoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return new File(java(project.getConvention()).getDocsDir(), "groovydoc");
                    }
                });
                groovydoc.getConventionMapping().map("docTitle", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
                groovydoc.getConventionMapping().map("windowTitle", new Callable<Object>() {
                    @Override
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
