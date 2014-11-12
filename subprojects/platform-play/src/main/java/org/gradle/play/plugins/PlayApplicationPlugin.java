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
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.DefaultPlayApplicationBinarySpec;
import org.gradle.play.internal.DefaultPlayApplicationSpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.platform.DefaultPlayPlatform;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.PlayRun;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
public class PlayApplicationPlugin implements Plugin<ProjectInternal> {
    public static final String DEFAULT_SCALA_BINARY_VERSION = "2.10";
    public static final String DEFAULT_PLAY_VERSION = "2.3.5";
    public static final String PLAY_GROUP = "com.typesafe.play";
    public static final String DEFAULT_PLAY_DEPENDENCY = PLAY_GROUP + ":play_" + DEFAULT_SCALA_BINARY_VERSION + ":" + DEFAULT_PLAY_VERSION;
    public static final String PLAYAPP_COMPILE_CONFIGURATION_NAME = "playAppCompile";
    public static final String PLAYAPP_RUNTIME_CONFIGURATION_NAME = "playAppRuntime";
    public static final String PLAY_MAIN_CLASS = "play.core.server.NettyServer";
    private ProjectInternal project;

    public void apply(final ProjectInternal project) {
        project.apply(WrapUtil.toMap("type", ScalaBasePlugin.class));
        this.project = project;
        setupPlayAppClasspath();
    }

    private void setupPlayAppClasspath() {
        final Configuration playAppCompileClasspath = createConfigurationWithDefaultDependency(PLAYAPP_COMPILE_CONFIGURATION_NAME, DEFAULT_PLAY_DEPENDENCY);
        playAppCompileClasspath.setDescription("The dependencies to be used for Scala compilation of a Play application.");

        project.getTasks().withType(ScalaCompile.class).all(new Action<ScalaCompile>() {
            public void execute(ScalaCompile scalaCompile) {
                scalaCompile.getConventionMapping().map("classpath", new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        return project.getConfigurations().getByName(PLAYAPP_COMPILE_CONFIGURATION_NAME);
                    }
                });
            }
        });

        final Configuration playAppRuntimeClasspath = project.getConfigurations().create(PLAYAPP_RUNTIME_CONFIGURATION_NAME);
        playAppRuntimeClasspath.extendsFrom(playAppCompileClasspath);
    }

    private Configuration createConfigurationWithDefaultDependency(String configurationName, final String defaultDependency) {
        final Configuration configuration = project.getConfigurations().create(configurationName);
        configuration.setVisible(false);

        configuration.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
            public void execute(ResolvableDependencies resolvableDependencies) {
                DependencySet dependencies = configuration.getDependencies();
                if (dependencies.isEmpty()) {
                    dependencies.add(project.getDependencies().create(defaultDependency));
                }
            }
        });
        return configuration;
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
            platforms.add(new DefaultPlayPlatform("2.3.5", "2.11", "1.0.2", JavaVersion.current()));
        }

        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

        @BinaryType
        void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
        }


        @ComponentBinaries
        void createBinaries(CollectionBuilder<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec, PlatformContainer platforms, final PlayToolChainInternal playToolChainInternal, @Path("buildDir") final File buildDir) {

            String targetPlayVersion = componentSpec.getPlayVersion();
            if (targetPlayVersion == null) {
                targetPlayVersion = "2.3.5";
            }

            List<PlayPlatform> selectedPlatforms = platforms.chooseFromTargets(PlayPlatform.class, WrapUtil.toList(String.format("PlayPlatform%s", targetPlayVersion)));
            for (final PlayPlatform selectedPlatform : selectedPlatforms) {
                binaries.create(String.format("%sBinary", componentSpec.getName()), new Action<PlayApplicationBinarySpec>() {
                    public void execute(PlayApplicationBinarySpec playBinary) {
                        PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;
                        playBinaryInternal.setTargetPlatform(selectedPlatform);
                        playBinaryInternal.setToolChain(playToolChainInternal);
                        playBinaryInternal.setJarFile(new File(buildDir, String.format("jars/%s/%s.jar", componentSpec.getName(), playBinaryInternal.getName())));
                    }
                });
            }
        }

        @BinaryTasks
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            final String twirlCompileTaskName = String.format("twirlCompile%s", StringUtils.capitalize(binary.getName()));
            final File twirlCompilerOutputDirectory = new File(buildDir, String.format("%s/twirl", binary.getName()));
            final File routesCompilerOutputDirectory = new File(buildDir, String.format("%s/src_managed", binary.getName()));

            tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
                public void execute(TwirlCompile twirlCompile) {
                    twirlCompile.setPlatform(binary.getTargetPlatform());
                    twirlCompile.setOutputDirectory(new File(twirlCompilerOutputDirectory, "views"));
                    twirlCompile.setSourceDirectory(new File(projectIdentifier.getProjectDir(), "app"));
                    twirlCompile.setSource(twirlCompile.getSourceDirectory());
                    twirlCompile.include("**/*.html");
                    binary.builtBy(twirlCompile);
                }
            });

            final String routesCompileTaskName = String.format("routesCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
                public void execute(RoutesCompile routesCompile) {
                    routesCompile.setPlatform(binary.getTargetPlatform());
                    routesCompile.setOutputDirectory(routesCompilerOutputDirectory);
                    routesCompile.setAdditionalImports(new ArrayList<String>());
                    routesCompile.setSource(new File(projectIdentifier.getProjectDir(), "conf"));
                    routesCompile.include("*.routes");
                    routesCompile.include("routes");
                    binary.builtBy(routesCompile);
                }
            });

            final String scalaCompileTaskName = String.format("scalaCompile%s", StringUtils.capitalize(binary.getName()));
            final File compileOutputDirectory = new File(buildDir, String.format("classes/%s/app", binary.getName()));
            tasks.create(scalaCompileTaskName, ScalaCompile.class, new Action<ScalaCompile>() {
                public void execute(ScalaCompile scalaCompile) {

                    scalaCompile.setDestinationDir(compileOutputDirectory);
                    scalaCompile.setSource("app");
                    IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                    incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", scalaCompileTaskName)));

                    // /ignore uncompiled twirl templates
                    scalaCompile.exclude("**/*.html");

                    // use zinc compiler per default
                    scalaCompile.getScalaCompileOptions().setFork(true);
                    scalaCompile.getScalaCompileOptions().setUseAnt(true);

                    //handle twirl compiler output
                    scalaCompile.dependsOn(twirlCompileTaskName);

                    //handle routes compiler
                    scalaCompile.dependsOn(routesCompileTaskName);

                    scalaCompile.source(twirlCompilerOutputDirectory);
                    scalaCompile.source(routesCompilerOutputDirectory);
                }
            });

            String jarTaskName = String.format("create%sJar", StringUtils.capitalize(binary.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getJarFile().getParentFile());
                    jar.setArchiveName(binary.getJarFile().getName());
                    jar.from(compileOutputDirectory);
                    jar.from("public");
                    jar.from("conf");
                    // CollectionBuilder api currently does not allow autowiring for tasks
                    jar.dependsOn(scalaCompileTaskName);
                }
            });
        }

        @Finalize
        void failOnMultiplePlayComponents(ComponentSpecContainer container) {
            if (container.withType(PlayApplicationSpec.class).size() >= 2) {
                throw new GradleException("Multiple components of type 'PlayApplicationSpec' are not supported.");
            }
        }

        @Mutate
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer) {
            for (final PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
                String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));
                tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                    public void execute(PlayRun playRun) {
                        playRun.dependsOn(binary.getBuildTask());

                        Project project = playRun.getProject();
                        Configuration playRunConf = project.getConfigurations().create("playRunConf");
                        playRunConf.getDependencies().add(new DefaultClientModule("com.typesafe.play", "play-docs_"+DEFAULT_SCALA_BINARY_VERSION, DEFAULT_PLAY_VERSION));
                        FileCollection classpath = project.files(binary.getJarFile()).plus(project.getConfigurations().getByName(PLAYAPP_RUNTIME_CONFIGURATION_NAME));
                        classpath.add(project.files(binary.getJarFile()).plus(playRunConf));
                        playRun.setClasspath(classpath); //TODO: not correct - should be only playRunCOnf
                        playRun.setPlayAppClasspath(classpath);
                    }
                });
            }
        }
    }
}
