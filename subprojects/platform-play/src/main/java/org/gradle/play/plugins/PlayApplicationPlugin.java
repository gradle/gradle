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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.scala.ScalaBasePlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.DefaultPlayApplicationBinarySpec;
import org.gradle.play.internal.DefaultPlayApplicationSpec;
import org.gradle.play.internal.DefaultPlayToolChain;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * Plugin for Play Framework component support.
 * Registers the {@link org.gradle.play.PlayApplicationSpec} component type for
 * the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
public class PlayApplicationPlugin implements Plugin<ProjectInternal> {
    public static final String DEFAULT_SCALA_BINARY_VERSION = "2.10";
    public static final String DEFAULT_PLAY_VERSION = "2.3.5";
    public static final String DEFAULT_PLAY_ID = DEFAULT_SCALA_BINARY_VERSION+"-"+DEFAULT_PLAY_VERSION;
    public static final String DEFAULT_PLAY_DEPENDENCY = "com.typesafe.play:play_"+DEFAULT_SCALA_BINARY_VERSION+":"+DEFAULT_PLAY_VERSION;
    public static final String DEFAULT_TWIRL_DEPENDENCY = "com.typesafe.play:twirl-compiler_"+DEFAULT_SCALA_BINARY_VERSION+":1.0.2";
    public static final String TWIRL_CONFIGURATION_NAME = "twirl";
    private static final String PLAYAPP_COMPILE_CONFIGURATION_NAME = "playAppCompile";
    public static final String DEFAULT_PLAY_ROUTES_DEPENDENCY = "com.typesafe.play:routes-compiler_"+DEFAULT_SCALA_BINARY_VERSION+":"+DEFAULT_PLAY_VERSION;
    public static final String PLAY_ROUTES_CONFIGURATION_NAME = "playRoutes";
    private ProjectInternal project;

    public void apply(final ProjectInternal project) {
        project.apply(WrapUtil.toMap("type", ScalaBasePlugin.class));
        this.project = project;
        setupTwirlCompilation();
        setupRoutesCompilation();
        setupPlayAppCompileClasspath();
    }

    private void setupPlayAppCompileClasspath() {
        final Configuration playAppCompileClasspath = project.getConfigurations().create(PLAYAPP_COMPILE_CONFIGURATION_NAME);
        playAppCompileClasspath.setVisible(false);
        playAppCompileClasspath.setDescription("The dependencies to be used for Scala compilation of a Play application.");

        playAppCompileClasspath.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
            public void execute(ResolvableDependencies resolvableDependencies) {
                DependencySet dependencies = playAppCompileClasspath.getDependencies();
                if (dependencies.isEmpty()) {
                    dependencies.add(project.getDependencies().create(DEFAULT_PLAY_DEPENDENCY));
                }
            }
        });

        project.getTasks().withType(ScalaCompile.class).all(new Action<ScalaCompile>(){
            public void execute(ScalaCompile scalaCompile) {
                scalaCompile.getConventionMapping().map("classpath", new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        return project.getConfigurations().getByName(PLAYAPP_COMPILE_CONFIGURATION_NAME);
                    }
                });
            }
        });
    }

    private void setupTwirlCompilation() {
        Configuration twirlConfiguration = createConfigurationWithDefaultDependency(TWIRL_CONFIGURATION_NAME, DEFAULT_TWIRL_DEPENDENCY);
        twirlConfiguration.setDescription("The dependencies to be used Play Twirl template compilation.");
        project.getTasks().withType(TwirlCompile.class).all(new Action<TwirlCompile>(){
            public void execute(TwirlCompile twirlCompile) {
                twirlCompile.getConventionMapping().map("compilerClasspath", new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        return project.getConfigurations().getByName(TWIRL_CONFIGURATION_NAME);
                    }
                });
            }
        });
    }

    private void setupRoutesCompilation() {
        Configuration routesConfiguration = createConfigurationWithDefaultDependency(PLAY_ROUTES_CONFIGURATION_NAME, DEFAULT_PLAY_ROUTES_DEPENDENCY);
        routesConfiguration.setVisible(false);
        routesConfiguration.setDescription("The dependencies to be used Play Routes compilation.");

        project.getTasks().withType(RoutesCompile.class).all(new Action<RoutesCompile>(){
            public void execute(RoutesCompile routesCompile) {
                routesCompile.getConventionMapping().map("compilerClasspath", new Callable<FileCollection>() {
                    public FileCollection call() throws Exception {
                        return project.getConfigurations().getByName(PLAY_ROUTES_CONFIGURATION_NAME);
                    }
                });
            }
        });
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

        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

        @BinaryType
        void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
        }

        @ComponentBinaries
        void createBinaries(CollectionBuilder<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec, @Path("buildDir") final File buildDir){
            binaries.create(String.format("%sBinary", componentSpec.getName()), new Action<PlayApplicationBinarySpec>(){
                public void execute(PlayApplicationBinarySpec playBinary) {
                    PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;
                    JavaVersion currentJava = JavaVersion.current();
                    playBinaryInternal.setTargetPlatform(new DefaultJavaPlatform(currentJava));
                    playBinaryInternal.setToolChain(new DefaultPlayToolChain(DEFAULT_PLAY_ID, currentJava));
                    playBinaryInternal.setJarFile(new File(buildDir, String.format("jars/%s/%s.jar", componentSpec.getName(), playBinaryInternal.getName())));
                }
            });
        }

        @BinaryTasks
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary, final ProjectIdentifier projectIdentifier,  @Path("buildDir") final File buildDir) {
            final String twirlCompileTaskName = String.format("twirlCompile%s", StringUtils.capitalize(binary.getName()));
            final File twirlCompilerOutputDirectory = new File(buildDir, String.format("%s/twirl", binary.getName()));
            final File routesCompilerOutputDirectory = new File(buildDir, String.format("%s/src_managed", binary.getName()));

            tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>(){
                public void execute(TwirlCompile twirlCompile) {
                    twirlCompile.setOutputDirectory(new File(twirlCompilerOutputDirectory, "views"));
                    twirlCompile.setSourceDirectory(new File(projectIdentifier.getProjectDir(), "app"));
                    twirlCompile.setSource(twirlCompile.getSourceDirectory());
                    twirlCompile.include("**/*.html");
                    binary.builtBy(twirlCompile);
                }
            });

            final String routesCompileTaskName = String.format("routesCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>(){
                public void execute(RoutesCompile routesCompile) {
                    routesCompile.setOutputDirectory(routesCompilerOutputDirectory);
                    routesCompile.setAdditionalImports(new ArrayList<String>());
                    routesCompile.setSource(new File(projectIdentifier.getProjectDir(), "conf/routes"));
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

                    //handle twirl compiler output
                    scalaCompile.dependsOn(twirlCompileTaskName);

                    //handle routes compiler
                    scalaCompile.dependsOn(routesCompileTaskName);

                    scalaCompile.source(twirlCompilerOutputDirectory);
                    scalaCompile.source(routesCompilerOutputDirectory);
                }
            });

            String jarTaskName = String.format("create%sJar", StringUtils.capitalize(binary.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>(){
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
    }
}
