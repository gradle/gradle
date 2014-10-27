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
import org.gradle.api.tasks.bundling.Jar;
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

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Plugin for Play Framework component support.
 * Registers the {@link org.gradle.play.PlayApplicationSpec} component type for
 * the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@Incubating
public class PlayApplicationPlugin implements Plugin<ProjectInternal> {
    public static final String DEFAULT_PLAY_VERSION = "2.11-2.3.5";
    public static final String DEFAULT_PLAY_DEPENDENCY = "com.typesafe.play:play_2.11:2.3.5";
    public static final String DEFAULT_TWIRL_DEPENDENCY = "com.typesafe.play:twirl-compiler_2.11:1.0.2";
    public static final String TWIRL_CONFIGURATION_NAME = "twirl";
    private ProjectInternal project;

    public void apply(final ProjectInternal project) {
        this.project = project;
        setupTwirlCompilation();
    }

    private void setupTwirlCompilation() {
        final Configuration twirlConfiguration = project.getConfigurations().create(TWIRL_CONFIGURATION_NAME);
        twirlConfiguration.setVisible(false);
        twirlConfiguration.setDescription("The dependencies to be used twirl template compilation.");

        twirlConfiguration.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
            public void execute(ResolvableDependencies resolvableDependencies) {
                DependencySet dependencies = twirlConfiguration.getDependencies();
                if (dependencies.isEmpty()) {
                    dependencies.add(project.getDependencies().create(DEFAULT_TWIRL_DEPENDENCY));
                }
            }
        });

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
                    playBinaryInternal.setToolChain(new DefaultPlayToolChain(DEFAULT_PLAY_VERSION, currentJava));
                    playBinaryInternal.setJarFile(new File(buildDir, String.format("jars/%s/%s.jar", componentSpec.getName(), playBinaryInternal.getName())));
                }
            });
        }

        @BinaryTasks
        void createPlayApplicationTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary, final ProjectIdentifier projectIdentifier,  @Path("buildDir") final File buildDir) {
            final String twirlCompileTaskName = String.format("twirlCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>(){
                public void execute(TwirlCompile twirlCompile) {
                    twirlCompile.setOutputDirectory(new File(buildDir, String.format("twirl/%s", binary.getName())));
                    twirlCompile.setSourceDirectory(new File(projectIdentifier.getProjectDir(), "app/views"));
                    binary.builtBy(twirlCompile);
                    System.out.println("twirlCompile.getSourceDirectory().getAbsolutePath() = " + twirlCompile.getSourceDirectory().getAbsolutePath());
                }
            });

            final String routesCompileTaskName = String.format("routesCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>(){
                public void execute(RoutesCompile routesCompile) {
                    binary.builtBy(routesCompile);
                }
            });


            tasks.create(String.format("create%sJar", StringUtils.capitalize(binary.getName())), Jar.class, new Action<Jar>(){
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getJarFile().getParentFile());
                    jar.setArchiveName(binary.getJarFile().getName());

                    // CollectionBuilder api currently does not allow autowiring for tasks
                    // later this does not depend on the different compile,
                    // but on the scala compile output
                    jar.dependsOn(routesCompileTaskName, twirlCompileTaskName);
                }
            });
        }
    }
}
