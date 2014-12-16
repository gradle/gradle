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
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.play.distribution.PlayDistribution;
import org.gradle.play.distribution.PlayDistributionContainer;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.distribution.DefaultPlayDistributionContainer;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * A plugin that adds a distribution zip to a Play application build.
 */
@SuppressWarnings("UnusedDeclaration")
@RuleSource
@Incubating
public class PlayDistributionPlugin {
    public static final String DISTRIBUTION_GROUP = "distribution";

    @Model
    PlayDistributionContainer distributions(ServiceRegistry serviceRegistry) {
        Instantiator instantiator = serviceRegistry.get(Instantiator.class);
        FileOperations fileOperations = serviceRegistry.get(FileOperations.class);

        return new DefaultPlayDistributionContainer(PlayDistribution.class, instantiator, fileOperations);
    }

    @Mutate
    void createLifecycleTasks(CollectionBuilder<Task> tasks) {
        tasks.create("dist", new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription("Assembles all play distributions.");
                task.setGroup(DISTRIBUTION_GROUP);
            }
        });

        tasks.create("stage", new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription("Stages all play distributions.");
                task.setGroup(DISTRIBUTION_GROUP);
            }
        });
    }

    @Mutate
    void createDistributions(@Path("distributions") PlayDistributionContainer distributions, BinaryContainer binaryContainer, final PlayToolChainInternal playToolChain) {
        for (PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
            PlayDistribution distribution = distributions.create(binary.getName());

            CopySpecInternal distSpec = (CopySpecInternal) distribution.getContents();
            CopySpec libSpec = distSpec.addChild().into("lib");
            libSpec.from(binary.getTasks().withType(Jar.class));
            libSpec.from(binary.getClasspath());
            CopySpec confSpec = distSpec.addChild().into("conf");
            confSpec.from("conf").exclude("routes");
            distSpec.from("README");

            distribution.setBinary(binary);
            distribution.setBaseName(binary.getName());
        }
    }

    @Mutate
    void createDistributionTasks(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer, final @Path("buildDir") File buildDir,
                                 final @Path("distributions") PlayDistributionContainer distributions, final PlayToolChainInternal playToolChain) {
        for (final PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
            final File scriptsDir = new File(buildDir, String.format("scripts/%s", binary.getName()));

            String createStartScriptsTaskName = String.format("create%sStartScripts", StringUtils.capitalize(binary.getName()));
            tasks.create(createStartScriptsTaskName, CreateStartScripts.class, new Action<CreateStartScripts>() {
                @Override
                public void execute(CreateStartScripts createStartScripts) {
                    createStartScripts.setDescription("Creates OS specific scripts to run the play application.");
                    createStartScripts.setClasspath(new UnionFileCollection(new SimpleFileCollection(binary.getJarFile(), binary.getAssetsJarFile()), binary.getClasspath()));
                    createStartScripts.setMainClassName("play.core.server.NettyServer");
                    createStartScripts.setApplicationName(binary.getName());
                    createStartScripts.setOutputDir(scriptsDir);

                    Spec<PlayDistribution> matchingBinary = new Spec<PlayDistribution>() {
                        @Override
                        public boolean isSatisfiedBy(PlayDistribution distribution) {
                            return distribution.getBinary() == binary;
                        }
                    };

                    for (PlayDistribution distribution : distributions.matching(matchingBinary)) {
                        CopySpecInternal distSpec = (CopySpecInternal) distribution.getContents();
                        CopySpec binSpec = distSpec.addChild().into("bin");
                        binSpec.from(createStartScripts);
                        binSpec.setFileMode(0755);
                    }
                }
            });
        }

        for (final PlayDistribution distribution : distributions) {
            String stageTaskName = String.format("stage%sDist", StringUtils.capitalize(distribution.getName()));
            tasks.create(stageTaskName, Copy.class, new Action<Copy>() {
                @Override
                public void execute(Copy copy) {
                    copy.setDescription("Copies the binary distribution to a staging directory.");
                    copy.setGroup(DISTRIBUTION_GROUP);
                    copy.setDestinationDir(new File(buildDir, "stage"));

                    String baseDirName = distribution.getName();
                    CopySpecInternal baseSpec = copy.getRootSpec().addChild();
                    baseSpec.into(baseDirName);
                    baseSpec.with(distribution.getContents());
                }
            });

            String distributionTaskName = String.format("create%sDist", StringUtils.capitalize(distribution.getName()));
            tasks.create(distributionTaskName, Zip.class, new Action<Zip>() {
                @Override
                public void execute(final Zip zip) {
                    zip.setDescription("Bundles the play binary as a distribution.");
                    zip.setGroup(DISTRIBUTION_GROUP);
                    zip.setBaseName(StringUtils.isNotEmpty(distribution.getBaseName()) ? distribution.getBaseName() : distribution.getName());
                    zip.setDestinationDir(new File(buildDir, "distributions"));

                    ConventionMapping mapping = zip.getConventionMapping();
                    mapping.map("version", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return zip.getProject().getVersion() == Project.DEFAULT_VERSION ? null : zip.getProject().getVersion().toString();
                        }
                    });

                    String baseDirName = zip.getArchiveName().substring(0, zip.getArchiveName().length() - zip.getExtension().length() - 1);
                    CopySpecInternal baseSpec = zip.getRootSpec().addChild();
                    baseSpec.into(baseDirName);
                    baseSpec.with(distribution.getContents());
                }
            });
        }
    }

    @Finalize
    void wireDistLifecycleDependencies(@Path("tasks.dist") Task distTask, TaskContainer tasks) {
        // TODO: Not sure this is the best way to do this, but it works for now
        distTask.dependsOn(tasks.withType(Zip.class).matching(new Spec<Zip>() {
            @Override
            public boolean isSatisfiedBy(Zip zipTask) {
                return DISTRIBUTION_GROUP.equals(zipTask.getGroup());
            }
        }));
    }

    @Finalize
    void wireStageLifecycleDependencies(@Path("tasks.stage") Task stageTask, TaskContainer tasks) {
        // TODO: Not sure this is the best way to do this, but it works for now
        stageTask.dependsOn(tasks.withType(Copy.class).matching(new Spec<Copy>() {
            @Override
            public boolean isSatisfiedBy(Copy copyTask) {
                return DISTRIBUTION_GROUP.equals(copyTask.getGroup());
            }
        }));
    }
}
