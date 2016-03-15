/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.gosu;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultGosuSourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GosuRuntime;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.gosu.GosuCompile;
import org.gradle.api.tasks.gosu.GosuDoc;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Gradle base plugin for the Gosu language
 */
public class GosuBasePlugin implements Plugin<Project> {
    public static final String GOSU_RUNTIME_EXTENSION_NAME = "gosuRuntime";

    private final SourceDirectorySetFactory sourceDirectorySetFactory;

    private Project project;
    private GosuRuntime gosuRuntime;

    @Inject
    GosuBasePlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
    }

    @Override
    public void apply(Project project) {
        this.project = project;
        this.project.getPluginManager().apply(JavaBasePlugin.class);

        JavaBasePlugin javaBasePlugin = this.project.getPlugins().getPlugin(JavaBasePlugin.class);

        configureGosuRuntimeExtension();
        configureCompileDefaults();
        configureSourceSetDefaults(javaBasePlugin);
        configureGosuDoc();
    }

    private void configureGosuRuntimeExtension() {
        gosuRuntime = project.getExtensions().create(GOSU_RUNTIME_EXTENSION_NAME, GosuRuntime.class, project);
    }

    /**
     * Sets the gosuClasspath property for all GosuCompile tasks: compileGosu and compileTestGosu
     */
    private void configureCompileDefaults() {
        project.getTasks().withType(GosuCompile.class, new Action<GosuCompile>() {
            public void execute(final GosuCompile gosuCompile) {
                gosuCompile.getConventionMapping().map("gosuClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return gosuRuntime.inferGosuClasspath(gosuCompile.getClasspath());
                    }
                });
            }
        });
    }

    private void configureSourceSetDefaults(final JavaBasePlugin javaBasePlugin) {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            public void execute(SourceSet sourceSet) {
                final DefaultGosuSourceSet gosuSourceSet = new DefaultGosuSourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), sourceDirectorySetFactory);
                new DslObject(sourceSet).getConvention().getPlugins().put("gosu", gosuSourceSet);

                gosuSourceSet.getGosu().srcDir("src/" + sourceSet.getName() + "/gosu");

                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return gosuSourceSet.getGosu().contains(element.getFile());
                    }
                });

                sourceSet.getAllJava().source(gosuSourceSet.getGosu());
                sourceSet.getAllSource().source(gosuSourceSet.getGosu());

                String compileTaskName = sourceSet.getCompileTaskName("gosu");
                GosuCompile gosuCompile = project.getTasks().create(compileTaskName, GosuCompile.class);
                javaBasePlugin.configureForSourceSet(sourceSet, gosuCompile);
                gosuCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                gosuCompile.setDescription("Compiles the " + sourceSet.getName() + " Gosu source.");
                gosuCompile.setSource(gosuSourceSet.getGosu());

                project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);

            }
        });
    }

    private void configureGosuDoc() {
        project.getTasks().withType(GosuDoc.class, new Action<GosuDoc>() {
            public void execute(final GosuDoc gosuDoc) {
                gosuDoc.getConventionMapping().map("gosuClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return gosuRuntime.inferGosuClasspath(gosuDoc.getClasspath());
                    }
                });
                gosuDoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(java(project.getConvention()).getDocsDir(), "gosudoc");
                    }
                });
                gosuDoc.getConventionMapping().map("title", new Callable<Object>() {
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
