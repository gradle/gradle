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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
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
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.play.JvmClasses;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.PublicAssets;
import org.gradle.play.internal.*;
import org.gradle.play.internal.platform.PlayPlatformInternal;
import org.gradle.play.internal.run.PlayApplicationDeploymentHandle;
import org.gradle.play.internal.run.PlayApplicationRunnerFactory;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.PlayRun;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the components container.
 */

@Incubating
public class PlayApplicationPlugin implements Plugin<Project> {
    public static final int DEFAULT_HTTP_PORT = 9000;
    public static final String RUN_GROUP = "Run";
    private final ModelRegistry modelRegistry;

    @Inject
    public PlayApplicationPlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLanguagePlugin.class);
        project.getPluginManager().apply(ScalaLanguagePlugin.class);
        project.getExtensions().create("playConfigurations", PlayPluginConfigurations.class, project.getConfigurations(), project.getDependencies());
        modelRegistry.getRoot().applyToAllLinksTransitive(ModelType.of(PlayApplicationSpec.class), PlaySourceSetRules.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        PlayPluginConfigurations configurations(ExtensionContainer extensions) {
            return extensions.getByType(PlayPluginConfigurations.class);
        }

        @Model
        PlayToolChainInternal playToolChain(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(PlayToolChainInternal.class);
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
        void createDefaultPlayApp(ModelMap<PlayApplicationSpec> builder) {
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

        @Validate
        void failOnMultiplePlayComponents(ModelMap<PlayApplicationSpec> container) {
            if (container.size() >= 2) {
                throw new GradleException("Multiple components of type 'PlayApplicationSpec' are not supported.");
            }
        }

        @Validate
        void failOnMultipleTargetPlatforms(ModelMap<PlayApplicationSpec> playApplications) {
            playApplications.afterEach(new Action<PlayApplicationSpec>() {
                public void execute(PlayApplicationSpec playApplication) {
                    PlayApplicationSpecInternal playApplicationInternal = (PlayApplicationSpecInternal) playApplication;
                    if (playApplicationInternal.getTargetPlatforms().size() > 1) {
                        throw new GradleException("Multiple target platforms for 'PlayApplicationSpec' is not (yet) supported.");
                    }
                }
            });
        }

        @Validate
        void failIfInjectedRouterIsUsedwithOldVersion(ModelMap<Task> tasks) {
            tasks.withType(RoutesCompile.class).afterEach(new Action<RoutesCompile>() {
                @Override
                public void execute(RoutesCompile task) {
                    PlayPlatform playPlatform = task.getPlatform();
                    if (!task.getStaticRoutesGenerator()) {
                        VersionNumber minSupportedVersion = VersionNumber.parse("2.4.0");
                        VersionNumber playVersion = VersionNumber.parse(playPlatform.getPlayVersion());
                        if (playVersion.compareTo(minSupportedVersion) < 0) {
                            throw new GradleException("Injected routers are only supported in Play 2.4 or newer.");
                        }
                    }
                }
            });
        }

        @ComponentBinaries
        void createBinaries(ModelMap<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec,
                            final PlatformResolvers platforms, final PlayToolChainInternal playToolChainInternal, final PlayPluginConfigurations configurations, final ServiceRegistry serviceRegistry,
                            @Path("buildDir") final File buildDir, final ProjectIdentifier projectIdentifier) {

            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final String binaryName = String.format("%sBinary", componentSpec.getName());

            binaries.create(binaryName, new Action<PlayApplicationBinarySpec>() {
                public void execute(PlayApplicationBinarySpec playBinary) {
                    PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;
                    playBinaryInternal.setApplication(componentSpec);
                    final File binaryBuildDir = new File(buildDir, binaryName);

                    final PlayPlatform chosenPlatform = resolveTargetPlatform(componentSpec, platforms);
                    initialiseConfigurations(configurations, chosenPlatform);

                    playBinaryInternal.setTargetPlatform(chosenPlatform);
                    playBinaryInternal.setToolChain(playToolChainInternal);

                    File mainJar = new File(binaryBuildDir, String.format("lib/%s.jar", projectIdentifier.getName()));
                    File assetsJar = new File(binaryBuildDir, String.format("lib/%s-assets.jar", projectIdentifier.getName()));
                    playBinaryInternal.setJarFile(mainJar);
                    playBinaryInternal.setAssetsJarFile(assetsJar);

                    configurations.getPlay().addArtifact(new DefaultPublishArtifact(projectIdentifier.getName(), "jar", "jar", null, new Date(), mainJar, playBinaryInternal));
                    configurations.getPlay().addArtifact(new DefaultPublishArtifact(projectIdentifier.getName(), "jar", "jar", "assets", new Date(), assetsJar, playBinaryInternal));

                    JvmClasses classes = playBinary.getClasses();
                    classes.setClassesDir(new File(binaryBuildDir, "classes"));

                    ModelMap<JvmResourceSet> jvmResourceSets = componentSpec.getSources().withType(JvmResourceSet.class);
                    for (JvmResourceSet jvmResourceSet : jvmResourceSets.values()) {
                        for (File resourceDir : jvmResourceSet.getSource()) {
                            classes.addResourceDir(resourceDir);
                        }
                    }

                    PublicAssets assets = playBinary.getAssets();
                    assets.addAssetDir(new File(projectIdentifier.getProjectDir(), "public"));

                    playBinaryInternal.setClasspath(configurations.getPlay().getAllArtifacts());
                }
            });
        }

        private PlayPlatform resolveTargetPlatform(PlayApplicationSpec componentSpec, final PlatformResolvers platforms) {
            PlatformRequirement targetPlatform = getTargetPlatform((PlayApplicationSpecInternal) componentSpec);
            return platforms.resolve(PlayPlatform.class, targetPlatform);
        }

        private PlatformRequirement getTargetPlatform(PlayApplicationSpecInternal playApplicationSpec) {
            if (playApplicationSpec.getTargetPlatforms().isEmpty()) {
                String defaultPlayPlatform = String.format("play-%s", DefaultPlayPlatform.DEFAULT_PLAY_VERSION);
                return DefaultPlatformRequirement.create(defaultPlayPlatform);
            }
            return playApplicationSpec.getTargetPlatforms().get(0);
        }

        private void initialiseConfigurations(PlayPluginConfigurations configurations, PlayPlatform playPlatform) {
            configurations.getPlayPlatform().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play"));
            configurations.getPlayTest().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play-test"));
            configurations.getPlayRun().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play-docs"));

            addRunSupportDependencies(configurations, playPlatform);
        }

        private void addRunSupportDependencies(PlayPluginConfigurations configurations, PlayPlatform playPlatform) {
            String playVersion = playPlatform.getPlayVersion();
            String scalaCompatibilityVersion = playPlatform.getScalaPlatform().getScalaCompatibilityVersion();
            Iterable<Dependency> runSupportDependencies = PlayApplicationRunnerFactory.createPlayRunAdapter(playPlatform).getRunsupportClasspathDependencies(playVersion, scalaCompatibilityVersion);
            for (Dependency dependencyNotation : runSupportDependencies) {
                configurations.getPlayRun().addDependency(dependencyNotation);
            }
        }

        @Mutate
        void createGeneratedScalaSourceSets(ModelMap<PlayApplicationBinarySpec> binaries, final ServiceRegistry serviceRegistry) {
            createGeneratedScalaSourceSetsForType(TwirlSourceSet.class, binaries, serviceRegistry);
            createGeneratedScalaSourceSetsForType(RoutesSourceSet.class, binaries, serviceRegistry);
        }

        void createGeneratedScalaSourceSetsForType(final Class<? extends LanguageSourceSet> languageSourceSetType, ModelMap<PlayApplicationBinarySpec> binaries, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            binaries.all(new Action<PlayApplicationBinarySpec>() {
                @Override
                public void execute(PlayApplicationBinarySpec playApplicationBinarySpec) {
                    for (LanguageSourceSet languageSourceSet : playApplicationBinarySpec.getInputs().withType(languageSourceSetType)) {
                        String name = String.format("%sScalaSources", languageSourceSet.getName());
                        ScalaLanguageSourceSet twirlScalaSources = BaseLanguageSourceSet.create(DefaultScalaLanguageSourceSet.class, name, playApplicationBinarySpec.getName(), fileResolver, instantiator);
                        playApplicationBinarySpec.getGeneratedScala().put(languageSourceSet, twirlScalaSources);
                    }
                }
            });
        }

        @BinaryTasks
        void createTwirlCompileTasks(ModelMap<Task> tasks, final PlayApplicationBinarySpec binary, ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir) {
            for (final TwirlSourceSet twirlSourceSet : binary.getInputs().withType(TwirlSourceSet.class)) {
                final String twirlCompileTaskName = String.format("compile%s%s", StringUtils.capitalize(binary.getName()), StringUtils.capitalize(twirlSourceSet.getName()));
                final File twirlCompileOutputDirectory = srcOutputDirectory(buildDir, binary, twirlCompileTaskName);

                tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
                    public void execute(TwirlCompile twirlCompile) {
                        twirlCompile.setDescription("Compiles twirl templates for the '" + twirlSourceSet.getName() + "' source set.");
                        twirlCompile.setPlatform(binary.getTargetPlatform());
                        twirlCompile.setSource(twirlSourceSet.getSource());
                        twirlCompile.setOutputDirectory(twirlCompileOutputDirectory);

                        ScalaLanguageSourceSet twirlScalaSources = binary.getGeneratedScala().get(twirlSourceSet);
                        twirlScalaSources.getSource().srcDir(twirlCompileOutputDirectory);
                        twirlScalaSources.builtBy(twirlCompile);
                    }
                });
            }
        }

        @BinaryTasks
        void createRoutesCompileTasks(ModelMap<Task> tasks, final PlayApplicationBinarySpec binary, ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir) {
            for (final RoutesSourceSet routesSourceSet : binary.getInputs().withType(RoutesSourceSet.class)) {
                final String routesCompileTaskName = String.format("compile%s%s", StringUtils.capitalize(binary.getName()), StringUtils.capitalize(routesSourceSet.getName()));
                final File routesCompilerOutputDirectory = srcOutputDirectory(buildDir, binary, routesCompileTaskName);

                tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
                    public void execute(RoutesCompile routesCompile) {
                        routesCompile.setDescription("Generates routes for the '" + routesSourceSet.getName() + "' source set.");
                        routesCompile.setPlatform(binary.getTargetPlatform());
                        routesCompile.setAdditionalImports(new ArrayList<String>());
                        routesCompile.setSource(routesSourceSet.getSource());
                        routesCompile.setOutputDirectory(routesCompilerOutputDirectory);
                        routesCompile.setStaticRoutesGenerator(binary.getApplication().getUseStaticRouter());

                        ScalaLanguageSourceSet routesScalaSources = binary.getGeneratedScala().get(routesSourceSet);
                        routesScalaSources.getSource().srcDir(routesCompilerOutputDirectory);
                        routesScalaSources.builtBy(routesCompile);
                    }
                });
            }
        }

        @BinaryTasks
        void createScalaCompileTask(ModelMap<Task> tasks, final PlayApplicationBinarySpec binary, @Path("buildDir") final File buildDir) {
            final String scalaCompileTaskName = String.format("compile%s%s", StringUtils.capitalize(binary.getName()), "Scala");
            tasks.create(scalaCompileTaskName, PlatformScalaCompile.class, new Action<PlatformScalaCompile>() {
                public void execute(PlatformScalaCompile scalaCompile) {
                    scalaCompile.setDescription("Compiles all scala and java source sets for the '" + binary.getName() + "' binary.");

                    scalaCompile.setDestinationDir(binary.getClasses().getClassesDir());
                    scalaCompile.setPlatform(binary.getTargetPlatform().getScalaPlatform());
                    //infer scala classpath
                    String targetCompatibility = binary.getTargetPlatform().getJavaPlatform().getTargetCompatibility().getMajorVersion();
                    scalaCompile.setSourceCompatibility(targetCompatibility);
                    scalaCompile.setTargetCompatibility(targetCompatibility);

                    IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                    incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", scalaCompileTaskName)));

                    for (LanguageSourceSet appSources : binary.getInputs().withType(ScalaLanguageSourceSet.class)) {
                        scalaCompile.source(appSources.getSource());
                        scalaCompile.dependsOn(appSources);
                    }

                    for (LanguageSourceSet appSources : binary.getInputs().withType(JavaSourceSet.class)) {
                        scalaCompile.source(appSources.getSource());
                        scalaCompile.dependsOn(appSources);
                    }

                    for (LanguageSourceSet generatedSourceSet : binary.getGeneratedScala().values()) {
                        scalaCompile.source(generatedSourceSet.getSource());
                        scalaCompile.dependsOn(generatedSourceSet);
                    }

                    scalaCompile.setClasspath(((PlayApplicationBinarySpecInternal) binary).getClasspath());

                    binary.getClasses().builtBy(scalaCompile);
                }
            });
        }

        @BinaryTasks
        void createJarTasks(ModelMap<Task> tasks, final PlayApplicationBinarySpec binary) {
            String jarTaskName = String.format("create%sJar", StringUtils.capitalize(binary.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDescription("Assembles the application jar for the '" + binary.getName() + "' binary.");
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
                    jar.setDescription("Assembles the assets jar for the '" + binary.getName() + "' binary.");
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

        @Mutate
        void createPlayRunTask(ModelMap<Task> tasks, BinaryContainer binaryContainer, final ServiceRegistry serviceRegistry, final PlayPluginConfigurations configurations, ProjectIdentifier projectIdentifier, final PlayToolChainInternal playToolChain) {

            for (final PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
                String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));

                tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                    public void execute(PlayRun playRun) {
                        playRun.setDescription("Runs the Play application for local development.");
                        playRun.setGroup(RUN_GROUP);
                        playRun.setHttpPort(DEFAULT_HTTP_PORT);
                        playRun.setPlayToolProvider(playToolChain.select(binary.getTargetPlatform()));
                        playRun.setApplicationJar(binary.getJarFile());
                        playRun.setAssetsJar(binary.getAssetsJarFile());
                        playRun.setAssetsDirs(binary.getAssets().getAssetDirs());
                        playRun.setRuntimeClasspath(configurations.getPlayRun().getNonChangingArtifacts());
                        playRun.setChangingClasspath(configurations.getPlayRun().getChangingArtifacts());
                        playRun.dependsOn(binary.getBuildTask());

                        DeploymentRegistry deploymentRegistry = serviceRegistry.get(DeploymentRegistry.class);
                        PlayApplicationDeploymentHandle deploymentHandle = deploymentRegistry.get(PlayApplicationDeploymentHandle.class, playRun.getPath());
                        deploymentHandle.registerBuildListener(serviceRegistry.get(Gradle.class));

                    }
                });
            }
        }

        private File srcOutputDirectory(File buildDir, PlayApplicationBinarySpec binary, String taskName) {
            return new File(buildDir, String.format("%s/src/%s", binary.getName(), taskName));
        }
    }
}
