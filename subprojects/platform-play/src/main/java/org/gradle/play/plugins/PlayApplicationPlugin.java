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
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.java.DefaultJvmResourceSet;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.internal.DefaultJavaLanguageSourceSet;
import org.gradle.language.java.plugins.JavaLanguagePlugin;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.routes.RoutesSourceSet;
import org.gradle.language.routes.internal.DefaultRoutesSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaLanguageSourceSet;
import org.gradle.language.scala.plugins.ScalaLanguagePlugin;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.language.twirl.TwirlSourceSet;
import org.gradle.language.twirl.internal.DefaultTwirlSourceSet;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.platform.base.internal.toolchain.ResolvedTool;
import org.gradle.platform.base.internal.toolchain.ToolResolver;
import org.gradle.play.JvmClasses;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.PublicAssets;
import org.gradle.play.internal.*;
import org.gradle.play.internal.platform.PlayPlatformInternal;
import org.gradle.play.internal.routes.RoutesCompileSpec;
import org.gradle.play.internal.run.PlayApplicationRunner;
import org.gradle.play.internal.twirl.TwirlCompileSpec;
import org.gradle.play.internal.twirl.TwirlCompilerFactory;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.PlayRun;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */

@Incubating
public class PlayApplicationPlugin implements Plugin<Project> {
    private final static String DEFAULT_PLAY_VERSION = "2.3.7";
    public static final int DEFAULT_HTTP_PORT = 9000;

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLanguagePlugin.class);
        project.getPluginManager().apply(ScalaLanguagePlugin.class);
        project.getExtensions().create("playConfigurations", PlayPluginConfigurations.class, project.getConfigurations(), project.getDependencies());
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        PlayPluginConfigurations configurations(ExtensionContainer extensions) {
            return extensions.getByType(PlayPluginConfigurations.class);
        }

        @Model
        FileResolver fileResolver(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(FileResolver.class);
        }

        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

        @Mutate
        public void registerPlatformResolver(PlatformResolvers platformResolvers) {
            platformResolvers.register(new PlayPlatformResolver());
        }

        @Mutate
        void createDefaultPlayApp(CollectionBuilder<PlayApplicationSpec> builder) {
            builder.create("play");
        }

        @BinaryType
        void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
        }

        @LanguageType
        void registerTwirlLanguageType(LanguageTypeBuilder<TwirlSourceSet> builder) {
            builder.setLanguageName("twirl");
            builder.defaultImplementation(DefaultTwirlSourceSet.class);
        }

        @LanguageType
        void registerRoutesLanguageType(LanguageTypeBuilder<RoutesSourceSet> builder) {
            builder.setLanguageName("routes");
            builder.defaultImplementation(DefaultRoutesSourceSet.class);
        }

        @Mutate
        void configureDefaultPlaySources(ComponentSpecContainer components, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            components.withType(PlayApplicationSpec.class).all(new Action<PlayApplicationSpec>() {
                public void execute(PlayApplicationSpec playComponent) {
                    // TODO:DAZ Scala source set type should be registered via scala-lang plugin
                    ScalaLanguageSourceSet scalaSources = BaseLanguageSourceSet.create(DefaultScalaLanguageSourceSet.class, "scala", playComponent.getName(), fileResolver, instantiator);

                    // Compile scala/java sources under /app\
                    // TODO:DAZ Should be selecting 'controllers/**' and 'model/**' I think, allowing user to add more includes
                    scalaSources.getSource().srcDir("app");
                    scalaSources.getSource().include("**/*.scala");
                    FunctionalSourceSet sources = ((ComponentSpecInternal) playComponent).getSources();
                    sources.add(scalaSources);

                    JavaSourceSet javaSources = BaseLanguageSourceSet.create(DefaultJavaLanguageSourceSet.class, "java", playComponent.getName(), fileResolver, instantiator);
                    javaSources.getSource().srcDir("app");
                    javaSources.getSource().include("**/*.java");
                    sources.add(javaSources);

                    DefaultSourceDirectorySet resourcesDirectorySet = new DefaultSourceDirectorySet("resources", fileResolver);
                    JvmResourceSet appResources = instantiator.newInstance(DefaultJvmResourceSet.class, "resources", playComponent.getName(), resourcesDirectorySet);
                    appResources.getSource().srcDirs("conf");
                    sources.add(appResources);
                }
            });
        }

        @Validate
        void failOnMultiplePlayComponents(CollectionBuilder<PlayApplicationSpec> container) {
            if (container.size() >= 2) {
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
                            final PlatformResolvers platforms, final PlayPluginConfigurations configurations, final ServiceRegistry serviceRegistry,
                            @Path("buildDir") final File buildDir, final ProjectIdentifier projectIdentifier) {

            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final ToolResolver toolResolver = serviceRegistry.get(ToolResolver.class);
            final String binaryName = String.format("%sBinary", componentSpec.getName());

            binaries.create(binaryName, new Action<PlayApplicationBinarySpec>() {
                public void execute(PlayApplicationBinarySpec playBinary) {
                    PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;
                    playBinaryInternal.setApplication(componentSpec);
                    final File binaryBuildDir = new File(buildDir, binaryName);

                    final PlayPlatform chosenPlatform = resolveTargetPlatform(componentSpec, platforms, configurations);
                    initialiseConfigurations(configurations, chosenPlatform);

                    playBinaryInternal.setTargetPlatform(chosenPlatform);
                    playBinaryInternal.setToolResolver(toolResolver);

                    File mainJar = new File(binaryBuildDir, String.format("lib/%s.jar", projectIdentifier.getName()));
                    File assetsJar = new File(binaryBuildDir, String.format("lib/%s-assets.jar", projectIdentifier.getName()));
                    playBinaryInternal.setJarFile(mainJar);
                    playBinaryInternal.setAssetsJarFile(assetsJar);

                    configurations.getPlay().addArtifact(new DefaultPublishArtifact(projectIdentifier.getName(), "jar", "jar", null, new Date(), mainJar, playBinaryInternal));
                    configurations.getPlay().addArtifact(new DefaultPublishArtifact(projectIdentifier.getName(), "jar", "jar", "assets", new Date(), assetsJar, playBinaryInternal));

                    JvmClasses classes = playBinary.getClasses();
                    classes.setClassesDir(new File(binaryBuildDir, "classes"));

                    DomainObjectSet<JvmResourceSet> jvmResourceSets = componentSpec.getSource().withType(JvmResourceSet.class);
                    for (JvmResourceSet jvmResourceSet : jvmResourceSets) {
                        for (File resourceDir : jvmResourceSet.getSource()) {
                            classes.addResourceDir(resourceDir);
                        }
                    }

                    // TODO:DAZ These should be configured on the component
                    PublicAssets assets = playBinary.getAssets();
                    assets.addAssetDir(new File(projectIdentifier.getProjectDir(), "public"));

                    playBinaryInternal.setClasspath(configurations.getPlay().getFileCollection());
                }
            });
        }

        private PlayPlatform resolveTargetPlatform(PlayApplicationSpec componentSpec, final PlatformResolvers platforms, PlayPluginConfigurations configurations) {
            PlatformRequirement targetPlatform = getTargetPlatform((PlayApplicationSpecInternal) componentSpec);
            return platforms.resolve(PlayPlatform.class, targetPlatform);
        }

        private PlatformRequirement getTargetPlatform(PlayApplicationSpecInternal playApplicationSpec) {
            if (playApplicationSpec.getTargetPlatforms().isEmpty()) {
                String defaultPlayPlatform = String.format("play-%s", DEFAULT_PLAY_VERSION);
                return DefaultPlatformRequirement.create(defaultPlayPlatform);
            }
            if (playApplicationSpec.getTargetPlatforms().size() == 1) {
                return playApplicationSpec.getTargetPlatforms().get(0);
            }
            throw new InvalidUserDataException("Play application can only target a single platform");
        }

        private void initialiseConfigurations(PlayPluginConfigurations configurations, PlayPlatform playPlatform) {
            configurations.getPlayPlatform().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play"));
            configurations.getPlayTest().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play-test"));
            configurations.getPlayRun().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play-docs"));
        }

        @Mutate
        void createTwirlSourceSets(CollectionBuilder<PlayApplicationSpec> components) {
            components.beforeEach(new Action<PlayApplicationSpec>() {
                @Override
                public void execute(PlayApplicationSpec playComponent) {
                    TwirlSourceSet twirlSourceSet = ((ComponentSpecInternal) playComponent).getSources().create("twirlTemplates", TwirlSourceSet.class);
                    twirlSourceSet.getSource().srcDir("app");
                    twirlSourceSet.getSource().include("**/*.html");
                }
            });
        }

        @Mutate
        void createRoutesSourceSets(CollectionBuilder<PlayApplicationSpec> components) {
            components.beforeEach(new Action<PlayApplicationSpec>() {
                @Override
                public void execute(PlayApplicationSpec playComponent) {
                    RoutesSourceSet routesSourceSet = ((ComponentSpecInternal) playComponent).getSources().create("routesSources", RoutesSourceSet.class);
                    routesSourceSet.getSource().srcDir("conf");
                    routesSourceSet.getSource().include("routes");
                    routesSourceSet.getSource().include("*.routes");
                }
            });
        }

        @Mutate
        void createGeneratedScalaSourceSets(CollectionBuilder<PlayApplicationBinarySpec> binaries, final ServiceRegistry serviceRegistry) {
            createGeneratedScalaSourceSetsForType(TwirlSourceSet.class, binaries, serviceRegistry);
            createGeneratedScalaSourceSetsForType(RoutesSourceSet.class, binaries, serviceRegistry);
        }

        void createGeneratedScalaSourceSetsForType(final Class<? extends LanguageSourceSet> languageSourceSetType, CollectionBuilder<PlayApplicationBinarySpec> binaries, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            binaries.all(new Action<PlayApplicationBinarySpec>() {
                @Override
                public void execute(PlayApplicationBinarySpec playApplicationBinarySpec) {
                    for (LanguageSourceSet languageSourceSet : playApplicationBinarySpec.getSource().withType(languageSourceSetType)) {
                        ScalaLanguageSourceSet twirlScalaSources = BaseLanguageSourceSet.create(DefaultScalaLanguageSourceSet.class, String.format("%sScalaSources", languageSourceSet.getName()), playApplicationBinarySpec.getName(), fileResolver, instantiator);
                        playApplicationBinarySpec.getGeneratedScala().put(languageSourceSet, twirlScalaSources);
                    }
                }
            });
        }

        @BinaryTasks
        void createTwirlCompileTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary, ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir) {
            final ToolResolver toolResolver = serviceRegistry.get(ToolResolver.class);
            final ResolvedTool<Compiler<TwirlCompileSpec>> compilerTool = toolResolver.resolveCompiler(TwirlCompileSpec.class, binary.getTargetPlatform());
            for (final TwirlSourceSet twirlSourceSet : binary.getSource().withType(TwirlSourceSet.class)) {
                final String twirlCompileTaskName = String.format("twirlCompile%s%s", StringUtils.capitalize(twirlSourceSet.getName()), StringUtils.capitalize(binary.getName()));
                final File twirlCompileOutputDirectory = srcOutputDirectory(buildDir, binary, twirlCompileTaskName);

                tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
                    public void execute(TwirlCompile twirlCompile) {
                        twirlCompile.setDependencyNotation(TwirlCompilerFactory.createAdapter(binary.getTargetPlatform()).getDependencyNotation());
                        twirlCompile.setSource(twirlSourceSet.getSource());
                        twirlCompile.setOutputDirectory(twirlCompileOutputDirectory);
                        twirlCompile.setCompilerTool(compilerTool);

                        ScalaLanguageSourceSet twirlScalaSources = binary.getGeneratedScala().get(twirlSourceSet);
                        twirlScalaSources.getSource().srcDir(twirlCompileOutputDirectory);
                        twirlScalaSources.builtBy(twirlCompile);
                    }
                });
            }
        }

        @BinaryTasks
        void createRoutesCompileTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary, ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir) {
            final ToolResolver toolResolver = serviceRegistry.get(ToolResolver.class);
            final ResolvedTool<Compiler<RoutesCompileSpec>> compilerTool = toolResolver.resolveCompiler(RoutesCompileSpec.class, binary.getTargetPlatform());
            for (final RoutesSourceSet routesSourceSet : binary.getSource().withType(RoutesSourceSet.class)) {
                final String routesCompileTaskName = String.format("routesCompile%s%s", StringUtils.capitalize(routesSourceSet.getName()), StringUtils.capitalize(binary.getName()));
                final File routesCompilerOutputDirectory = srcOutputDirectory(buildDir, binary, routesCompileTaskName);

                tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
                    public void execute(RoutesCompile routesCompile) {
                        routesCompile.setCompilerTool(compilerTool);
                        routesCompile.setAdditionalImports(new ArrayList<String>());
                        routesCompile.setSource(routesSourceSet.getSource());
                        routesCompile.setOutputDirectory(routesCompilerOutputDirectory);

                        ScalaLanguageSourceSet routesScalaSources = binary.getGeneratedScala().get(routesSourceSet);
                        routesScalaSources.getSource().srcDir(routesCompilerOutputDirectory);
                        routesScalaSources.builtBy(routesCompile);
                    }
                });
            }
        }

        @BinaryTasks
        void createScalaCompileTask(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary, @Path("buildDir") final File buildDir) {
            final String scalaCompileTaskName = String.format("scalaCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(scalaCompileTaskName, PlatformScalaCompile.class, new Action<PlatformScalaCompile>() {
                public void execute(PlatformScalaCompile scalaCompile) {

                    scalaCompile.setDestinationDir(binary.getClasses().getClassesDir());
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

                    for (LanguageSourceSet appSources : binary.getSource().withType(JavaSourceSet.class)) {
                        scalaCompile.source(appSources.getSource());
                        scalaCompile.dependsOn(appSources);
                    }

                    for (LanguageSourceSet generatedSourceSet : binary.getGeneratedScala().values()) {
                        scalaCompile.source(generatedSourceSet.getSource());
                        scalaCompile.dependsOn(generatedSourceSet.getBuildDependencies());
                    }

                    scalaCompile.setClasspath(((PlayApplicationBinarySpecInternal)binary).getClasspath());

                    binary.getClasses().builtBy(scalaCompile);
                }
            });
        }

        @BinaryTasks
        void createJarTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary) {
            String jarTaskName = String.format("create%sJar", StringUtils.capitalize(binary.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getJarFile().getParentFile());
                    jar.setArchiveName(binary.getJarFile().getName());
                    jar.from(binary.getClasses().getClassesDir());
                    jar.from(binary.getClasses().getResourceDirs());
                    jar.dependsOn(binary.getClasses());
                }
            });

            String assetsJarTaskName = String.format("create%sAssetsJar", StringUtils.capitalize(binary.getName()));
            tasks.create(assetsJarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getAssetsJarFile().getParentFile());
                    jar.setArchiveName(binary.getAssetsJarFile().getName());
                    jar.setClassifier("assets");
                    CopySpecInternal newSpec = jar.getRootSpec().addChild();
                    newSpec.from(binary.getAssets().getAssetDirs());
                    newSpec.into("public");
                    jar.dependsOn(binary.getAssets());
                }
            });
        }

        // TODO:DAZ Need a nice way to create tasks that are associated with a binary but not part of _building_ it.
        @Mutate
        void createPlayRunTask(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer, ServiceRegistry serviceRegistry, final PlayPluginConfigurations configurations) {
            ToolResolver toolResolver = serviceRegistry.get(ToolResolver.class);
            for (final PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
                final ResolvedTool<PlayApplicationRunner> playApplicationRunnerTool = toolResolver.resolve(PlayApplicationRunner.class, binary.getTargetPlatform());
                String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));
                tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                    public void execute(PlayRun playRun) {
                        playRun.setHttpPort(DEFAULT_HTTP_PORT);
                        playRun.setPlayApplicationRunnerTool(playApplicationRunnerTool);
                        playRun.setApplicationJar(binary.getJarFile());
                        playRun.setAssetsJar(binary.getAssetsJarFile());
                        playRun.setRuntimeClasspath(configurations.getPlayRun().getFileCollection());
                        playRun.dependsOn(binary.getBuildTask());
                    }
                });
            }
        }

        private File srcOutputDirectory(File buildDir, PlayApplicationBinarySpec binary, String taskName) {
            return new File(buildDir, String.format("%s/src/%s", binary.getName(), taskName));
        }
    }
}
