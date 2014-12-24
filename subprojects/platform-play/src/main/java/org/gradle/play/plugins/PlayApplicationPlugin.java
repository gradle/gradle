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
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaPlatform;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.ManagedSet;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolver;
import org.gradle.play.JvmClasses;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.PublicAssets;
import org.gradle.play.internal.DefaultPlayApplicationBinarySpec;
import org.gradle.play.internal.DefaultPlayApplicationSpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.PlayApplicationSpecInternal;
import org.gradle.play.internal.platform.PlayPlatformInternal;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.PlayRun;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */
@SuppressWarnings("UnusedDeclaration")
@RuleSource
@Incubating
public class PlayApplicationPlugin {
    private final static String DEFAULT_PLAY_VERSION = "2.3.7";
    public static final int DEFAULT_HTTP_PORT = 9000;

    @Model
    void playPlatforms(ManagedSet<PlayPlatformInternal> playPlatforms) {
        playPlatforms.create(new Action<PlayPlatformInternal>() {
            public void execute(PlayPlatformInternal platform) {
                initializePlatform(platform, "2.2.3", "2.10.3", "2.2.3");
            }
        });
        playPlatforms.create(new Action<PlayPlatformInternal>() {
            public void execute(PlayPlatformInternal platform) {
                initializePlatform(platform, DEFAULT_PLAY_VERSION, "2.11.1", "1.0.2");
            }
        });
    }

    private void initializePlatform(PlayPlatformInternal platform, String playVersion, String scalaVersion, String twirlVersion) {
        platform.setName("play-" + playVersion);
        platform.setDisplayName(String.format("Play Platform (Play %s, Scala: %s, JDK %s (%s))", playVersion, scalaVersion, JavaVersion.current().getMajorVersion(), JavaVersion.current()));
        platform.setPlayVersion(playVersion);
        platform.setScalaPlatform(new DefaultScalaPlatform(scalaVersion));
        platform.setTwirlVersion(twirlVersion);
        platform.setJavaPlatform(new DefaultJavaPlatform(JavaVersion.current()));
    }

    @Model
    PlayToolChainInternal playToolChain(ServiceRegistry serviceRegistry) {
        return serviceRegistry.get(PlayToolChainInternal.class);
    }

    @Model
    FileResolver fileResolver(ServiceRegistry serviceRegistry) {
        return serviceRegistry.get(FileResolver.class);
    }

    @Mutate
    public void addPlayPlatformsToPlatformContainer(PlatformContainer platforms, ManagedSet<PlayPlatformInternal> playPlatformInternals) {
        platforms.addAll(playPlatformInternals);
    }

    @ComponentType
    void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
        builder.defaultImplementation(DefaultPlayApplicationSpec.class);
    }

    @Mutate
    void createDefaultPlayApp(CollectionBuilder<PlayApplicationSpec> builder) {
        builder.create("play");
    }

    @BinaryType
    void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
        builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
    }

    @Mutate
    void configureDefaultPlaySources(ComponentSpecContainer components, final FileResolver fileResolver) {
        components.withType(PlayApplicationSpec.class).all(new Action<PlayApplicationSpec>() {
            public void execute(PlayApplicationSpec playComponent) {
                // TODO:DAZ Scala source set type should be registered via scala-lang plugin
                ScalaLanguageSourceSet appSources = new DefaultScalaLanguageSourceSet("appSources", playComponent.getName(), fileResolver);

                // Compile scala/java sources under /app\
                // TODO:DAZ Should be selecting 'controllers/**' and 'model/**' I think, allowing user to add more includes
                appSources.getSource().srcDir("app");
                appSources.getSource().include("**/*.scala");
                appSources.getSource().include("**/*.java");
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

    @Finalize
    void failOnMultipleTargetPlatforms(ComponentSpecContainer container) {
        for (PlayApplicationSpecInternal playApplicationSpec : container.withType(PlayApplicationSpecInternal.class)) {
            if (playApplicationSpec.getTargetPlatforms().size() > 1) {
                throw new GradleException("Multiple target platforms for 'PlayApplicationSpec' is not (yet) supported.");
            }
        }
    }

    @ComponentBinaries
    void createBinaries(CollectionBuilder<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec,
                        PlatformResolver platforms, final PlayToolChainInternal playToolChainInternal,
                        final FileResolver fileResolver, @Path("buildDir") final File buildDir, final ProjectIdentifier projectIdentifier) {
        for (final PlayPlatform chosenPlatform : resolveTargetPlatforms(componentSpec, platforms)) {
            final String binaryName = String.format("%sBinary", componentSpec.getName());
            final File binaryBuildDir = new File(buildDir, binaryName);
            binaries.create(binaryName, new Action<PlayApplicationBinarySpec>() {
                public void execute(PlayApplicationBinarySpec playBinary) {
                    PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;

                    playBinaryInternal.setTargetPlatform(chosenPlatform);
                    playBinaryInternal.setToolChain(playToolChainInternal);

                    playBinaryInternal.setJarFile(new File(binaryBuildDir, String.format("lib/%s.jar", componentSpec.getName())));

                    JvmClasses classes = playBinary.getClasses();
                    classes.setClassesDir(new File(binaryBuildDir, "classes"));

                    // TODO:DAZ These should be configured on the component
                    classes.addResourceDir(new File(projectIdentifier.getProjectDir(), "conf"));

                    PublicAssets assets = playBinary.getAssets();
                    assets.addAssetDir(new File(projectIdentifier.getProjectDir(), "public"));

                    ScalaLanguageSourceSet genSources = new DefaultScalaLanguageSourceSet("genSources", binaryName, fileResolver);
                    playBinaryInternal.setGeneratedScala(genSources);
                }
            });
        }
    }

    private List<PlayPlatform> resolveTargetPlatforms(PlayApplicationSpec componentSpec, PlatformResolver platforms) {
        List<PlatformRequirement> targetPlatforms = ((PlayApplicationSpecInternal) componentSpec).getTargetPlatforms();
        if (targetPlatforms.isEmpty()) {
            String defaultPlayPlatform = String.format("play-%s", DEFAULT_PLAY_VERSION);
            targetPlatforms = Collections.singletonList(DefaultPlatformRequirement.create(defaultPlayPlatform));
        }
        return platforms.resolve(PlayPlatform.class, targetPlatforms);
    }

    @BinaryTasks
    void createTwirlCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
        final String twirlCompileTaskName = String.format("twirlCompile%s", StringUtils.capitalize(binary.getName()));
        tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
            public void execute(TwirlCompile twirlCompile) {
                twirlCompile.setPlatform(binary.getTargetPlatform());
                twirlCompile.setSourceDirectory(new File(projectIdentifier.getProjectDir(), "app"));
                twirlCompile.include("**/*.html");

                File twirlCompilerOutputDirectory = new File(buildDir, String.format("%s/src/%s", binary.getName(), twirlCompileTaskName));
                twirlCompile.setOutputDirectory(twirlCompilerOutputDirectory);

                binary.getGeneratedScala().getSource().srcDir(twirlCompilerOutputDirectory);
                binary.getGeneratedScala().builtBy(twirlCompile);
            }
        });
    }

    @BinaryTasks
    void createRoutesCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
        final String routesCompileTaskName = String.format("routesCompile%s", StringUtils.capitalize(binary.getName()));
        tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
            public void execute(RoutesCompile routesCompile) {
                routesCompile.setPlatform(binary.getTargetPlatform());
                routesCompile.setAdditionalImports(new ArrayList<String>());
                routesCompile.setSource(new File(projectIdentifier.getProjectDir(), "conf"));
                routesCompile.include("*.routes");
                routesCompile.include("routes");

                final File routesCompilerOutputDirectory = new File(buildDir, String.format("%s/src/%s", binary.getName(), routesCompileTaskName));
                routesCompile.setOutputDirectory(routesCompilerOutputDirectory);

                binary.getGeneratedScala().getSource().srcDir(routesCompilerOutputDirectory);
                binary.getGeneratedScala().builtBy(routesCompile);
            }
        });
    }

    @BinaryTasks
    void createScalaCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary,
                            PlayToolChainInternal playToolChain, FileResolver fileResolver, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
        final FileCollection playDependencies = playToolChain.select(binary.getTargetPlatform()).getPlayDependencies();
        final String scalaCompileTaskName = String.format("scalaCompile%s", StringUtils.capitalize(binary.getName()));
        tasks.create(scalaCompileTaskName, PlatformScalaCompile.class, new Action<PlatformScalaCompile>() {
            public void execute(PlatformScalaCompile scalaCompile) {
                scalaCompile.setDestinationDir(binary.getClasses().getClassesDir());
                scalaCompile.setClasspath(playDependencies);
                scalaCompile.setPlatform(binary.getTargetPlatform().getScalaPlatform());
                //infer scala classpath
                String targetCompatibility = binary.getTargetPlatform().getJavaPlatform().getTargetCompatibility().getMajorVersion();
                scalaCompile.setSourceCompatibility(targetCompatibility);
                scalaCompile.setTargetCompatibility(targetCompatibility);

                IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", scalaCompileTaskName)));

                for (LanguageSourceSet appSources : binary.getSource().withType(ScalaLanguageSourceSet.class)) {
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
                jar.from(binary.getClasses().getResourceDirs());
                CopySpecInternal newSpec = jar.getRootSpec().addChild();
                newSpec.from(binary.getAssets().getAssetDirs());
                newSpec.into("public");
                jar.dependsOn(binary.getClasses());
                jar.dependsOn(binary.getAssets());
            }
        });
    }

    // TODO:DAZ Need a nice way to create tasks that are associated with a binary but not part of _building_ it.
    @Mutate
    void createPlayRunTask(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer) {
        for (final PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
            String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));
            tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                public void execute(PlayRun playRun) {
                    playRun.setHttpPort(DEFAULT_HTTP_PORT);
                    playRun.setTargetPlatform(binary.getTargetPlatform());
                    playRun.setApplicationJar(binary.getJarFile());
                    playRun.dependsOn(binary.getBuildTask());
                }
            });
        }
    }
}
