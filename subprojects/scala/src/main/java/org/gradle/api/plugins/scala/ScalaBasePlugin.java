/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.plugins.scala;

import com.google.common.annotations.VisibleForTesting;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.internal.tasks.DefaultScalaSourceSet;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.internal.SourceSetUtil;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaDoc;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.scala.internal.toolchain.DefaultScalaToolProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} which compiles and tests Scala sources.</p>
 */
public class ScalaBasePlugin implements Plugin<Project> {

    @VisibleForTesting
    public static final String ZINC_CONFIGURATION_NAME = "zinc";
    public static final String SCALA_RUNTIME_EXTENSION_NAME = "scalaRuntime";
    private final SourceDirectorySetFactory sourceDirectorySetFactory;

    @Inject
    public ScalaBasePlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
        this.sourceDirectorySetFactory = sourceDirectorySetFactory;
    }

    public void apply(Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        configureConfigurations(project);
        ScalaRuntime scalaRuntime = configureScalaRuntimeExtension(project);
        configureCompileDefaults(project, scalaRuntime);
        configureSourceSetDefaults(project, sourceDirectorySetFactory);
        configureScaladoc(project, scalaRuntime);
    }

    private static void configureConfigurations(final Project project) {
        project.getConfigurations().create(ZINC_CONFIGURATION_NAME).setVisible(false).setDescription("The Zinc incremental compiler to be used for this Scala project.")
            .defaultDependencies(new Action<DependencySet>() {
                @Override
                public void execute(DependencySet dependencies) {
                    dependencies.add(project.getDependencies().create("com.typesafe.zinc:zinc:" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION));
                }
            });
    }

    private static ScalaRuntime configureScalaRuntimeExtension(Project project) {
        return project.getExtensions().create(SCALA_RUNTIME_EXTENSION_NAME, ScalaRuntime.class, project);
    }

    private static void configureSourceSetDefaults(final Project project, final SourceDirectorySetFactory sourceDirectorySetFactory) {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                String displayName = (String) InvokerHelper.invokeMethod(sourceSet, "getDisplayName", null);
                Convention sourceSetConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
                DefaultScalaSourceSet scalaSourceSet = new DefaultScalaSourceSet(displayName, sourceDirectorySetFactory);
                sourceSetConvention.getPlugins().put("scala", scalaSourceSet);
                final SourceDirectorySet scalaDirectorySet = scalaSourceSet.getScala();
                scalaDirectorySet.srcDir(new Callable<File>() {
                    @Override
                    public File call() throws Exception {
                        return project.file("src/" + sourceSet.getName() + "/scala");
                    }
                });
                sourceSet.getAllJava().source(scalaDirectorySet);
                sourceSet.getAllSource().source(scalaDirectorySet);
                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    @Override
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return scalaDirectorySet.contains(element.getFile());
                    }
                });

                configureScalaCompile(project, sourceSet);
            }

        });
    }

    private static void configureScalaCompile(final Project project, final SourceSet sourceSet) {
        Convention scalaConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
        final ScalaSourceSet scalaSourceSet = scalaConvention.findPlugin(ScalaSourceSet.class);
        SourceSetUtil.configureOutputDirectoryForSourceSet(sourceSet, scalaSourceSet.getScala(), project);
        final TaskProvider<ScalaCompile> scalaCompile = project.getTasks().register(sourceSet.getCompileTaskName("scala"), ScalaCompile.class, new Action<ScalaCompile>() {
            @Override
            public void execute(ScalaCompile scalaCompile) {
                scalaCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                SourceSetUtil.configureForSourceSet(sourceSet, scalaSourceSet.getScala(), scalaCompile, scalaCompile.getOptions(), project);
                scalaCompile.setDescription("Compiles the " + scalaSourceSet.getScala() + ".");
                scalaCompile.setSource(scalaSourceSet.getScala());


                // cannot use convention mapping because the resulting object won't be serializable
                // cannot compute at task execution time because we need association with source set
                // TODO: Replace this with Providers
                IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                if (incrementalOptions.getAnalysisFile() == null) {
                    String analysisFilePath = project.getBuildDir().getPath() + "/tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".analysis";
                    incrementalOptions.setAnalysisFile(new File(analysisFilePath));
                }

                if (incrementalOptions.getPublishedCode() == null) {
                    Jar jarTask = (Jar) project.getTasks().findByName(sourceSet.getJarTaskName());
                    incrementalOptions.setPublishedCode(jarTask == null ? null : jarTask.getArchivePath());
                }
            }
        });

        project.getTasks().named(sourceSet.getClassesTaskName()).configure(new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(scalaCompile);
            }
        });
    }

    private static void configureCompileDefaults(final Project project, final ScalaRuntime scalaRuntime) {
        project.getTasks().withType(ScalaCompile.class).configureEach(new Action<ScalaCompile>() {
            @Override
            public void execute(final ScalaCompile compile) {
                compile.getConventionMapping().map("scalaClasspath", new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        return scalaRuntime.inferScalaClasspath(compile.getClasspath());
                    }
                });
                compile.getConventionMapping().map("zincClasspath", new Callable<Configuration>() {
                    @Override
                    public Configuration call() throws Exception {
                        return project.getConfigurations().getAt(ZINC_CONFIGURATION_NAME);
                    }
                });
            }
        });
    }

    private static void configureScaladoc(final Project project, final ScalaRuntime scalaRuntime) {
        project.getTasks().withType(ScalaDoc.class).configureEach(new Action<ScalaDoc>() {
            @Override
            public void execute(final ScalaDoc scalaDoc) {
                scalaDoc.getConventionMapping().map("destinationDir", new Callable<File>() {
                    @Override
                    public File call() throws Exception {
                        File docsDir = project.getConvention().getPlugin(JavaPluginConvention.class).getDocsDir();
                        return project.file(docsDir.getPath() + "/scaladoc");
                    }
                });
                scalaDoc.getConventionMapping().map("title", new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
                scalaDoc.getConventionMapping().map("scalaClasspath", new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        return scalaRuntime.inferScalaClasspath(scalaDoc.getClasspath());
                    }
                });
            }
        });
    }
}
