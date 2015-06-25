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

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.model.*;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.distribution.PlayDistribution;
import org.gradle.play.distribution.PlayDistributionContainer;
import org.gradle.play.internal.distribution.DefaultPlayDistribution;
import org.gradle.play.internal.distribution.DefaultPlayDistributionContainer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * A plugin that adds a distribution zip to a Play application build.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayDistributionPlugin extends RuleSource {
    public static final String DISTRIBUTION_GROUP = "distribution";

    @Model
    PlayDistributionContainer distributions(ServiceRegistry serviceRegistry) {
        Instantiator instantiator = serviceRegistry.get(Instantiator.class);
        return new DefaultPlayDistributionContainer(instantiator);
    }

    @Mutate
    void createLifecycleTasks(ModelMap<Task> tasks) {
        tasks.create("dist", new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription("Assembles all Play distributions.");
                task.setGroup(DISTRIBUTION_GROUP);
            }
        });

        tasks.create("stage", new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription("Stages all Play distributions.");
                task.setGroup(DISTRIBUTION_GROUP);
            }
        });
    }

    @Defaults
    void createDistributions(@Path("distributions") PlayDistributionContainer distributions, BinaryContainer binaryContainer, PlayPluginConfigurations configurations, ServiceRegistry serviceRegistry) {
        FileOperations fileOperations = serviceRegistry.get(FileOperations.class);
        Instantiator instantiator = serviceRegistry.get(Instantiator.class);
        for (PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
            PlayDistribution distribution = instantiator.newInstance(DefaultPlayDistribution.class, binary.getName(), fileOperations.copySpec(), binary);
            distribution.setBaseName(binary.getName());
            distributions.add(distribution);
        }
    }

    @Mutate
    void createDistributionContentTasks(ModelMap<Task> tasks, final @Path("buildDir") File buildDir,
                                        final @Path("distributions") PlayDistributionContainer distributions,
                                        final PlayPluginConfigurations configurations) {
        for (PlayDistribution distribution : distributions.withType(PlayDistribution.class)) {
            final PlayApplicationBinarySpec binary = distribution.getBinary();
            if (binary == null) {
                throw new InvalidUserCodeException(String.format("Play Distribution '%s' does not have a configured Play binary.", distribution.getName()));
            }

            final File distJarDir = new File(buildDir, String.format("distributionJars/%s", distribution.getName()));
            final String jarTaskName = String.format("create%sDistributionJar", StringUtils.capitalize(distribution.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    jar.dependsOn(binary.getTasks().withType(Jar.class));
                    jar.from(jar.getProject().zipTree(binary.getJarFile()));
                    jar.setDestinationDir(distJarDir);
                    jar.setArchiveName(binary.getJarFile().getName());

                    Map<String, Object> classpath = Maps.newHashMap();
                    classpath.put("Class-Path", new PlayManifestClasspath(configurations.getPlayRun(), binary.getAssetsJarFile()));
                    jar.getManifest().attributes(classpath);
                }
            });
            final Task distributionJar = tasks.get(jarTaskName);

            final File scriptsDir = new File(buildDir, String.format("scripts/%s", distribution.getName()));
            String createStartScriptsTaskName = String.format("create%sStartScripts", StringUtils.capitalize(distribution.getName()));
            tasks.create(createStartScriptsTaskName, CreateStartScripts.class, new Action<CreateStartScripts>() {
                @Override
                public void execute(CreateStartScripts createStartScripts) {
                    createStartScripts.setDescription("Creates OS specific scripts to run the Play application.");
                    createStartScripts.setClasspath(distributionJar.getOutputs().getFiles());
                    createStartScripts.setMainClassName("play.core.server.NettyServer");
                    createStartScripts.setApplicationName(binary.getName());
                    createStartScripts.setOutputDir(scriptsDir);
                }
            });
            Task createStartScripts = tasks.get(createStartScriptsTaskName);

            CopySpecInternal distSpec = (CopySpecInternal) distribution.getContents();
            CopySpec libSpec = distSpec.addChild().into("lib");
            libSpec.from(distributionJar);
            libSpec.from(binary.getAssetsJarFile());
            libSpec.from(configurations.getPlayRun().getFileCollection());

            CopySpec binSpec = distSpec.addChild().into("bin");
            binSpec.from(createStartScripts);
            binSpec.setFileMode(0755);

            CopySpec confSpec = distSpec.addChild().into("conf");
            confSpec.from("conf").exclude("routes");
            distSpec.from("README");
        }
    }

    @Mutate
    void createDistributionZipTasks(ModelMap<Task> tasks, final @Path("buildDir") File buildDir, PlayDistributionContainer distributions) {
        for (final PlayDistribution distribution : distributions.withType(PlayDistribution.class)) {
            final String stageTaskName = String.format("stage%sDist", StringUtils.capitalize(distribution.getName()));
            final File stageDir = new File(buildDir, "stage");
            final String baseName = StringUtils.isNotEmpty(distribution.getBaseName()) ? distribution.getBaseName() : distribution.getName();
            tasks.create(stageTaskName, Copy.class, new Action<Copy>() {
                @Override
                public void execute(Copy copy) {
                    copy.setDescription("Copies the binary distribution to a staging directory.");
                    copy.setGroup(DISTRIBUTION_GROUP);
                    copy.setDestinationDir(stageDir);

                    CopySpecInternal baseSpec = copy.getRootSpec().addChild();
                    baseSpec.into(baseName);
                    baseSpec.with(distribution.getContents());
                }
            });
            tasks.named("stage", new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(stageTaskName);
                }
            });

            final Task stageTask = tasks.get(stageTaskName);
            final String distributionTaskName = String.format("create%sDist", StringUtils.capitalize(distribution.getName()));
            tasks.create(distributionTaskName, Zip.class, new Action<Zip>() {
                @Override
                public void execute(final Zip zip) {
                    zip.setDescription("Bundles the Play binary as a distribution.");
                    zip.setGroup(DISTRIBUTION_GROUP);
                    zip.setArchiveName(String.format("%s.zip", baseName));
                    zip.setDestinationDir(new File(buildDir, "distributions"));
                    zip.from(stageTask);
                }
            });

            tasks.named("dist", new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(distributionTaskName);
                }
            });
        }
    }

    /**
     * Represents a classpath to be defined in a jar manifest
     */
    static class PlayManifestClasspath {
        final PlayPluginConfigurations.PlayConfiguration playConfiguration;
        final File assetsJarFile;

        public PlayManifestClasspath(PlayPluginConfigurations.PlayConfiguration playConfiguration, File assetsJarFile) {
            this.playConfiguration = playConfiguration;
            this.assetsJarFile = assetsJarFile;
        }

        @Override
        public String toString() {
            Set<File> classpathFiles = playConfiguration.getFileCollection().getFiles();
            classpathFiles.add(assetsJarFile);
            Set<String> classpathFileNames = CollectionUtils.collect(classpathFiles, new Transformer<String, File>() {
                @Override
                public String transform(File file) {
                    return file.getName();
                }
            });

            return StringUtils.join(classpathFileNames, " ");
        }
    }
}
