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
import com.google.common.collect.ImmutableSet;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultScalaSourceSet;
import org.gradle.api.model.ObjectFactory;
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
import org.gradle.api.tasks.compile.CompileOptions;
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
    private final ObjectFactory objectFactory;

    @Inject
    public ScalaBasePlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        Usage incrementalAnalysisUsage = objectFactory.named(Usage.class, "incremental-analysis");
        configureConfigurations(project, incrementalAnalysisUsage);

        ScalaRuntime scalaRuntime = project.getExtensions().create(SCALA_RUNTIME_EXTENSION_NAME, ScalaRuntime.class, project);

        configureCompileDefaults(project, scalaRuntime);
        configureSourceSetDefaults(project, incrementalAnalysisUsage, objectFactory);
        configureScaladoc(project, scalaRuntime);
    }

    private void configureConfigurations(final Project project, final Usage incrementalAnalysisUsage) {
        project.getConfigurations().create(ZINC_CONFIGURATION_NAME).setVisible(false).setDescription("The Zinc incremental compiler to be used for this Scala project.")
            .defaultDependencies(new Action<DependencySet>() {
                @Override
                public void execute(DependencySet dependencies) {
                    dependencies.add(project.getDependencies().create("com.typesafe.zinc:zinc:" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION));
                }
            });

        final Configuration incrementalAnalysisElements = project.getConfigurations().create("incrementalScalaAnalysisElements");
        incrementalAnalysisElements.setVisible(false);
        incrementalAnalysisElements.setDescription("Incremental compilation analysis files");
        incrementalAnalysisElements.setCanBeResolved(false);
        incrementalAnalysisElements.setCanBeConsumed(true);
        incrementalAnalysisElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, incrementalAnalysisUsage);

        AttributeMatchingStrategy<Usage> matchingStrategy = project.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE);
        matchingStrategy.getDisambiguationRules().add(UsageDisambiguationRules.class, new Action<ActionConfiguration>() {
            @Override
            public void execute(ActionConfiguration actionConfiguration) {
                actionConfiguration.params(incrementalAnalysisUsage);
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_API));
                actionConfiguration.params(objectFactory.named(Usage.class, Usage.JAVA_RUNTIME_JARS));
            }
        });
    }

    private static void configureSourceSetDefaults(final Project project, final Usage incrementalAnalysisUsage, final ObjectFactory objectFactory) {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                String displayName = (String) InvokerHelper.invokeMethod(sourceSet, "getDisplayName", null);
                Convention sourceSetConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
                DefaultScalaSourceSet scalaSourceSet = new DefaultScalaSourceSet(displayName, objectFactory);
                sourceSetConvention.getPlugins().put("scala", scalaSourceSet);

                final SourceDirectorySet scalaDirectorySet = scalaSourceSet.getScala();
                scalaDirectorySet.srcDir(project.file("src/" + sourceSet.getName() + "/scala"));
                sourceSet.getAllJava().source(scalaDirectorySet);
                sourceSet.getAllSource().source(scalaDirectorySet);
                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    @Override
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return scalaDirectorySet.contains(element.getFile());
                    }
                });

                Configuration classpath = project.getConfigurations().getByName(sourceSet.getImplementationConfigurationName());
                Configuration incrementalAnalysis = project.getConfigurations().create("incrementalScalaAnalysisFor" + sourceSet.getName());
                incrementalAnalysis.setVisible(false);
                incrementalAnalysis.setDescription("Incremental compilation analysis files for " + displayName);
                incrementalAnalysis.setCanBeResolved(true);
                incrementalAnalysis.setCanBeConsumed(false);
                incrementalAnalysis.extendsFrom(classpath);
                incrementalAnalysis.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, incrementalAnalysisUsage);

                configureScalaCompile(project, sourceSet, incrementalAnalysis, incrementalAnalysisUsage);
            }

        });
    }

    private static void configureScalaCompile(final Project project, final SourceSet sourceSet, final Configuration incrementalAnalysis, final Usage incrementalAnalysisUsage) {
        Convention scalaConvention = (Convention) InvokerHelper.getProperty(sourceSet, "convention");
        final ScalaSourceSet scalaSourceSet = scalaConvention.findPlugin(ScalaSourceSet.class);

        final TaskProvider<ScalaCompile> scalaCompile = project.getTasks().register(sourceSet.getCompileTaskName("scala"), ScalaCompile.class, new Action<ScalaCompile>() {
            @Override
            public void execute(ScalaCompile scalaCompile) {
                scalaCompile.dependsOn(sourceSet.getCompileJavaTaskName());
                SourceSetUtil.configureForSourceSet(sourceSet, scalaSourceSet.getScala(), scalaCompile, scalaCompile.getOptions(), project);
                scalaCompile.setDescription("Compiles the " + scalaSourceSet.getScala() + ".");
                scalaCompile.setSource(scalaSourceSet.getScala());

                scalaCompile.getAnalysisMappingFile().set(project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".mapping"));

                // cannot compute at task execution time because we need association with source set
                IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                incrementalOptions.getAnalysisFile().set(
                    project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + scalaCompile.getName() + ".analysis")
                );

                final Jar jarTask = (Jar) project.getTasks().findByName(sourceSet.getJarTaskName());
                if (jarTask != null) {
                    incrementalOptions.getPublishedCode().set(jarTask.getArchiveFile());
                }
                scalaCompile.getAnalysisFiles().from(incrementalAnalysis.getIncoming().artifactView(new Action<ArtifactView.ViewConfiguration>() {
                    @Override
                    public void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                        viewConfiguration.lenient(true);
                        viewConfiguration.componentFilter(new Spec<ComponentIdentifier>() {
                            @Override
                            public boolean isSatisfiedBy(ComponentIdentifier element) {
                                return element instanceof ProjectComponentIdentifier;
                            }
                        });
                    }
                }).getFiles());
            }
        });
        SourceSetUtil.configureOutputDirectoryForSourceSet(sourceSet, scalaSourceSet.getScala(), project, scalaCompile, scalaCompile.map(new Transformer<CompileOptions, ScalaCompile>() {
            @Override
            public CompileOptions transform(ScalaCompile scalaCompile) {
                return scalaCompile.getOptions();
            }
        }));

        project.getTasks().named(sourceSet.getClassesTaskName(), new Action<Task>() {
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

    static class UsageDisambiguationRules implements AttributeDisambiguationRule<Usage> {
        private final ImmutableSet<Usage> expectedUsages;
        private final Usage javaRuntimeJars;

        @Inject
        UsageDisambiguationRules(Usage incrementalAnalysis, Usage javaApi, Usage javaRuntimeJars) {
            this.javaRuntimeJars = javaRuntimeJars;
            this.expectedUsages = ImmutableSet.of(incrementalAnalysis, javaApi, javaRuntimeJars);
        }

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getConsumerValue() == null) {
                if (details.getCandidateValues().equals(expectedUsages)) {
                    details.closestMatch(javaRuntimeJars);
                }
            }
        }
    }
}
