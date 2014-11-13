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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.ScalaRuntime;
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
import org.gradle.play.toolchain.PlayToolChain;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
public class PlayApplicationPlugin implements Plugin<ProjectInternal> {

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
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, ServiceRegistry serviceRegistry, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
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
            final File compileOutputDirectory = new File(buildDir, String.format("classes/%s/app", binary.getName()));
            tasks.create(scalaCompileTaskName, ScalaCompile.class, new Action<ScalaCompile>() {
                public void execute(ScalaCompile scalaCompile) {
                    scalaCompile.setDestinationDir(compileOutputDirectory);
                    scalaCompile.setClasspath(appCompileClasspath);
                    scalaCompile.setScalaClasspath(new ScalaRuntime(scalaCompile.getProject()).inferScalaClasspath(appCompileClasspath));
                    scalaCompile.setZincClasspath(zincClasspath);
                    scalaCompile.setSource("app");
                    //infer scala classpath
                    scalaCompile.setSourceCompatibility(binary.getTargetPlatform().getJavaVersion().getMajorVersion());
                    scalaCompile.setTargetCompatibility(binary.getTargetPlatform().getJavaVersion().getMajorVersion());

                    IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                    incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", scalaCompileTaskName)));

                    // /ignore uncompiled twirl templates
                    scalaCompile.exclude("**/*.html");

                    // use zinc compiler per default
                    scalaCompile.getScalaCompileOptions().setFork(true);
                    scalaCompile.getScalaCompileOptions().setUseAnt(false);

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
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer, ServiceRegistry serviceRegistry, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            for (final PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
                String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));
                tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                    public void execute(PlayRun playRun) {
                        playRun.dependsOn(binary.getBuildTask());
                        Project project = playRun.getProject();
                        Configuration playRunConf = project.getConfigurations().create("playRunConf");
//                        playRunConf.getDependencies().add(new DefaultClientModule("com.typesafe.play", "play-docs_"+DEFAULT_SCALA_BINARY_VERSION, DEFAULT_PLAY_VERSION));
//                        FileCollection classpath = project.files(binary.getJarFile()).plus(project.getConfigurations().getByName(PLAYAPP_RUNTIME_CONFIGURATION_NAME));
//                        classpath.add(project.files(binary.getJarFile()).plus(playRunConf));
//                        playRun.setClasspath(classpath); //TODO: not correct - should be only playRunCOnf
//                        playRun.setPlayAppClasspath(classpath);
                    }
                });

            }
        }
    }
}
