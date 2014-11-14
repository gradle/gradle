/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.play.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.ScalaRuntime;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.play.JvmClasses;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.DefaultPlayApplicationBinarySpec;
import org.gradle.play.internal.DefaultPlayApplicationSpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.ScalaSources;
import org.gradle.play.internal.platform.DefaultPlayPlatform;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.PlayRun;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;
import org.gradle.play.toolchain.PlayToolChain;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
public class PlayApplicationPlugin implements Plugin<ProjectInternal> {
    private final static String DEFAULT_PLAY_VERSION = "2.3.5";
    public static final int DEFAULT_HTTP_PORT = 9000;

    public void apply(final ProjectInternal project) {
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {

        @Model
        PlayToolChainInternal playToolChain(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(PlayToolChainInternal.class);
        }

        @Mutate
        public void createPlayPlatforms(PlatformContainer platforms) {
            platforms.add(new DefaultPlayPlatform("2.2.3", "2.10", "2.2.3", JavaVersion.current()));
            platforms.add(new DefaultPlayPlatform(DEFAULT_PLAY_VERSION, "2.11", "1.0.2", JavaVersion.current()));
        }

        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

        @BinaryType
        void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
        }

        @Mutate
        void configureDefaultPlaySources(ComponentSpecContainer components, final ServiceRegistry serviceRegistry) {
            components.withType(PlayApplicationSpec.class).all(new Action<PlayApplicationSpec>() {
                public void execute(PlayApplicationSpec playComponent) {
                    // TODO:DAZ Scala source set type should be registered
                    ScalaSources appSources = new ScalaSources("appSources", playComponent.getName(), serviceRegistry.get(FileResolver.class));
                    // Compile everything under /app, except for raw twirl templates
                    // TODO:DAZ This is wrong: we need to exclude javascript/coffeescript as well. Should select scala/java directories.
                    appSources.getSource().srcDir("app");
                    appSources.getSource().exclude("**/*.html");

                    ((ComponentSpecInternal) playComponent).getSources().add(appSources);
                }
            });
        }

        @Finalize
        void failOnMultiplePlayComponents(ComponentSpecContainer container) {
            if (container.withType(PlayApplicationSpec.class).size() >= 2) {
                throw new GradleException("Multiple components of type 'PlayApplicationSpec' are not supported.");
            }
        }

        @ComponentBinaries
        void createBinaries(CollectionBuilder<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec,
                            PlatformContainer platforms, final PlayToolChainInternal playToolChainInternal,
                            final ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir, final ProjectIdentifier projectIdentifier) {
            for (final PlayPlatform chosenPlatform : getChosenPlatforms(componentSpec, platforms)) {
                final String binaryName = String.format("%sBinary", componentSpec.getName());
                binaries.create(binaryName, new Action<PlayApplicationBinarySpec>() {
                    public void execute(PlayApplicationBinarySpec playBinary) {
                        PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;

                        // TODO:DAZ This shouldn't be required: should be part of infrastructure
                        FunctionalSourceSet binarySourceSet = ((ComponentSpecInternal) componentSpec).getSources().copy(binaryName);
                        playBinaryInternal.setBinarySources(binarySourceSet);

                        playBinaryInternal.setTargetPlatform(chosenPlatform);
                        playBinaryInternal.setToolChain(playToolChainInternal);

                        playBinaryInternal.setJarFile(new File(buildDir, String.format("jars/%s/%s.jar", componentSpec.getName(), playBinaryInternal.getName())));

                        JvmClasses classes = playBinary.getClasses();
                        classes.setClassesDir(new File(buildDir, String.format("classes/%s", binaryName)));

                        // TODO:DAZ These should be configured on the component
                        classes.addResourceDir(new File(projectIdentifier.getProjectDir(), "conf"));
                        classes.addResourceDir(new File(projectIdentifier.getProjectDir(), "public"));

                        ScalaSources genSources = new ScalaSources("genSources", binaryName, serviceRegistry.get(FileResolver.class));
                        playBinaryInternal.setGeneratedScala(genSources);
                    }
                });
            }
        }

        private List<PlayPlatform> getChosenPlatforms(PlayApplicationSpec componentSpec, PlatformContainer platforms) {
            String targetPlayVersion = componentSpec.getPlayVersion();
            if (targetPlayVersion == null) {
                targetPlayVersion = DEFAULT_PLAY_VERSION;
            }
            return platforms.chooseFromTargets(PlayPlatform.class, WrapUtil.toList(String.format("PlayPlatform%s", targetPlayVersion)));
        }

        @BinaryTasks
        void createTwirlCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary,
                                final ServiceRegistry serviceRegistry, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            final String twirlCompileTaskName = String.format("twirlCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
                public void execute(TwirlCompile twirlCompile) {
                    File twirlCompilerOutputDirectory = new File(buildDir, String.format("%s/twirl", binary.getName()));
                    twirlCompile.setPlatform(binary.getTargetPlatform());
                    twirlCompile.setOutputDirectory(new File(twirlCompilerOutputDirectory, "views"));
                    twirlCompile.setSourceDirectory(new File(projectIdentifier.getProjectDir(), "app"));
                    twirlCompile.include("**/*.html");

                    binary.getGeneratedScala().getSource().srcDir(twirlCompilerOutputDirectory);
                    binary.getGeneratedScala().builtBy(twirlCompile);
                }
            });
        }

        @BinaryTasks
        void createRoutesCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary,
                                final ServiceRegistry serviceRegistry, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            final String routesCompileTaskName = String.format("routesCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
                public void execute(RoutesCompile routesCompile) {
                    final File routesCompilerOutputDirectory = new File(buildDir, String.format("%s/src_managed", binary.getName()));
                    routesCompile.setPlatform(binary.getTargetPlatform());
                    routesCompile.setOutputDirectory(routesCompilerOutputDirectory);
                    routesCompile.setAdditionalImports(new ArrayList<String>());
                    routesCompile.setSource(new File(projectIdentifier.getProjectDir(), "conf"));
                    routesCompile.include("*.routes");
                    routesCompile.include("routes");

                    binary.getGeneratedScala().getSource().srcDir(routesCompilerOutputDirectory);
                    binary.getGeneratedScala().builtBy(routesCompile);
                }
            });
        }

        @BinaryTasks
        void createScalaCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary,
                                ServiceRegistry serviceRegistry, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            //load compile dependencies for scalaCompile
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            ConfigurationContainer configurationContainer = serviceRegistry.get(ConfigurationContainer.class);
            DependencyHandler dependencyHandler = serviceRegistry.get(DependencyHandler.class);
            PlayToolChain playToolChain = serviceRegistry.get(PlayToolChain.class);
            final Dependency playDependency = dependencyHandler.create(playToolChain.getPlayDependencyNotationForPlatform(binary.getTargetPlatform()));
            final Configuration appCompileClasspath = configurationContainer.detachedConfiguration(playDependency);

            Dependency zincDependency = dependencyHandler.create(String.format("com.typesafe.zinc:zinc:%s", ScalaBasePlugin.DEFAULT_ZINC_VERSION));
            final Configuration zincClasspath = configurationContainer.detachedConfiguration(zincDependency);

            final String scalaCompileTaskName = String.format("scalaCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(scalaCompileTaskName, ScalaCompile.class, new Action<ScalaCompile>() {
                public void execute(ScalaCompile scalaCompile) {
                    scalaCompile.setDestinationDir(binary.getClasses().getClassesDir());
                    scalaCompile.setClasspath(appCompileClasspath);
                    scalaCompile.setScalaClasspath(new ScalaRuntime(scalaCompile.getProject()).inferScalaClasspath(appCompileClasspath));
                    scalaCompile.setZincClasspath(zincClasspath);

                    //infer scala classpath
                    scalaCompile.setSourceCompatibility(binary.getTargetPlatform().getJavaVersion().getMajorVersion());
                    scalaCompile.setTargetCompatibility(binary.getTargetPlatform().getJavaVersion().getMajorVersion());

                    IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                    incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", scalaCompileTaskName)));

                    // use zinc compiler per default
                    scalaCompile.getScalaCompileOptions().setFork(true);
                    scalaCompile.getScalaCompileOptions().setUseAnt(false);

                    // TODO:DAZ Source sets should be typed for scala
                    for (LanguageSourceSet appSources : binary.getSource()) {
                        scalaCompile.source(appSources.getSource());
                        scalaCompile.dependsOn(appSources);
                    }
                    scalaCompile.source(binary.getGeneratedScala().getSource());
                    scalaCompile.dependsOn(binary.getGeneratedScala());

                    binary.getClasses().builtBy(scalaCompile);
                }
            });
        }

        @BinaryTasks
        void createJarTask(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary) {
            String jarTaskName = String.format("create%sJar", StringUtils.capitalize(binary.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getJarFile().getParentFile());
                    jar.setArchiveName(binary.getJarFile().getName());

                    jar.from(binary.getClasses().getClassesDir());
                    for (File resourceDir : binary.getClasses().getResourceDirs()) {
                        jar.from(resourceDir);
                    }
                    jar.dependsOn(binary.getClasses());
                }
            });
        }

        // TODO:DAZ Need a nice way to create tasks that are associated with a binary but not part of _building_ it.
        // TODO:Rene/Freekh Break this up a little
        @Mutate
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer, ServiceRegistry serviceRegistry, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            for (final PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
                final File testCompileOutputDirectory = new File(buildDir, String.format("testClasses/%s", binary.getName()));
                final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
                final ConfigurationContainer configurationContainer = serviceRegistry.get(ConfigurationContainer.class);
                final DependencyHandler dependencyHandler = serviceRegistry.get(DependencyHandler.class);

                String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));
                tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                    public void execute(PlayRun playRun) {
                        playRun.setHttpPort(DEFAULT_HTTP_PORT);
                        playRun.setTargetPlatform(binary.getTargetPlatform());
                        Project project = playRun.getProject();

                        //TODO move to ToolChain:
                        Dependency playDependency = dependencyHandler.create(String.format("com.typesafe.play:play-test_%s:%s", binary.getTargetPlatform().getScalaVersion(), binary.getTargetPlatform().getPlayVersion()));
                        final Configuration playCompileConfiguration = configurationContainer.detachedConfiguration(playDependency);
                        playRun.setClasspath(playCompileConfiguration);

                        playRun.dependsOn(binary.getBuildTask());
                    }
                });


                PlayToolChain playToolChain = serviceRegistry.get(PlayToolChain.class);
                PlayPlatform targetPlatform = binary.getTargetPlatform();

                // TODO the knowledge about platform dependencies should be moved into toolchain/toolprovider
                Dependency playTestDependency = dependencyHandler.create(String.format("com.typesafe.play:play-test_%s:%s", targetPlatform.getScalaVersion(), targetPlatform.getPlayVersion()));
                final Configuration testCompileConfiguration = configurationContainer.detachedConfiguration(playTestDependency);

                Dependency zincDependency = dependencyHandler.create(String.format("com.typesafe.zinc:zinc:%s", ScalaBasePlugin.DEFAULT_ZINC_VERSION));
                final Configuration zincClasspath = configurationContainer.detachedConfiguration(zincDependency);

                //setup testcompile classpath
                final FileCollection testCompileClasspath = fileResolver.resolveFiles(binary.getJarFile()).plus(testCompileConfiguration);
                final String testCompileTaskName = String.format("compile%sTests", StringUtils.capitalize(binary.getName()));
                tasks.create(testCompileTaskName, ScalaCompile.class, new Action<ScalaCompile>() {
                    public void execute(ScalaCompile scalaCompile) {
                        scalaCompile.dependsOn(binary.getBuildTask());
                        scalaCompile.setClasspath(testCompileClasspath);
                        scalaCompile.setZincClasspath(zincClasspath);
                        scalaCompile.setScalaClasspath(new ScalaRuntime(scalaCompile.getProject()).inferScalaClasspath(testCompileClasspath));
                        scalaCompile.setDestinationDir(testCompileOutputDirectory);
                        scalaCompile.setSource("test");
                        scalaCompile.setSourceCompatibility(binary.getTargetPlatform().getJavaVersion().getMajorVersion());
                        scalaCompile.setTargetCompatibility(binary.getTargetPlatform().getJavaVersion().getMajorVersion());
                        IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                        incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", testCompileTaskName)));

                        // use zinc compiler per default
                        scalaCompile.getScalaCompileOptions().setFork(true);
                        scalaCompile.getScalaCompileOptions().setUseAnt(false);
                    }
                });

                String testTaskName = String.format("test%s", StringUtils.capitalize(binary.getName()));
                tasks.create(testTaskName, Test.class, new Action<Test>() {
                    public void execute(Test test) {
                        test.setTestClassesDir(testCompileOutputDirectory);
                        test.setBinResultsDir(new File(buildDir, String.format("binTestResultsDir/%s", binary.getName())));
                        test.getReports().getJunitXml().setDestination(new File(buildDir, String.format("reports/test/%s/test-results", binary.getName())));
                        test.getReports().getHtml().setDestination(new File(buildDir, String.format("reports/test/%s/html", binary.getName())));
                        test.dependsOn(testCompileTaskName);
                        test.setTestSrcDirs(Arrays.asList(fileResolver.resolve("test")));
                        test.setWorkingDir(projectIdentifier.getProjectDir());
                        test.setClasspath(testCompileClasspath.plus(fileResolver.resolveFiles(testCompileOutputDirectory)));
                    }
                });
            }
        }
    }
}
