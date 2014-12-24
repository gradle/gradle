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
import org.gradle.api.Task;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.internal.DefaultDistributionContainer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;

import java.io.File;

/**
 * A plugin that adds a distribution zip to a Play application build.
 */
@SuppressWarnings("UnusedDeclaration")
@RuleSource
@Incubating
public class PlayDistributionPlugin {
    @Model
    DistributionContainer distributions(ServiceRegistry serviceRegistry) {
        Instantiator instantiator = serviceRegistry.get(Instantiator.class);
        FileOperations fileOperations = serviceRegistry.get(FileOperations.class);

        return new DefaultDistributionContainer(Distribution.class, instantiator, fileOperations);
    }

    @Mutate
    void configureDistributions(@Path("distributions") DistributionContainer distributions, BinaryContainer binaryContainer, final PlayToolChainInternal playToolChain) {
        for (PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
            Distribution distribution = distributions.create(binary.getName());

            FileCollection playDependencies = playToolChain.select(binary.getTargetPlatform()).getPlayDependencies();
            CopySpecInternal distSpec = (CopySpecInternal) distributions.findByName(binary.getName()).getContents();
            CopySpec libSpec = distSpec.addChild().into("lib");
            libSpec.from(binary.getJarFile(), binary.getAssetsJarFile());
            libSpec.from(playDependencies);
        }
    }

    @BinaryTasks
    void createDistributionTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final @Path("buildDir") File buildDir, final @Path("distributions") DistributionContainer distributions, final PlayToolChainInternal playToolChain) {
        final File scriptsDir = new File(buildDir, String.format("scripts/%s", binary.getName()));
        final FileCollection playDependencies = playToolChain.select(binary.getTargetPlatform()).getPlayDependencies();

        String createStartScriptsTaskName = String.format("create%sStartScripts", StringUtils.capitalize(binary.getName()));
        tasks.create(createStartScriptsTaskName, CreateStartScripts.class, new Action<CreateStartScripts>() {
            @Override
            public void execute(CreateStartScripts createStartScripts) {
                createStartScripts.setDescription("Creates OS specific scripts to run the play application.");
                createStartScripts.setClasspath(new UnionFileCollection(new SimpleFileCollection(binary.getJarFile(), binary.getAssetsJarFile()), playDependencies));
                createStartScripts.setMainClassName("play.core.server.NettyServer");
                createStartScripts.setApplicationName(binary.getName());
                createStartScripts.setOutputDir(scriptsDir);

                CopySpecInternal distSpec = (CopySpecInternal) distributions.findByName(binary.getName()).getContents();
                distSpec.addChild().into("bin").from(createStartScripts);
            }
        });

        String distTaskName = String.format("create%sDist", StringUtils.capitalize(binary.getName()));
        tasks.create(distTaskName, Zip.class, new Action<Zip>() {
            @Override
            public void execute(Zip zip) {
                zip.setDescription("Bundles the project as a distribution.");
                zip.setGroup("distribution");
                zip.setBaseName(binary.getName());
                zip.setDestinationDir(new File(buildDir, "distributions"));

                String baseDirName = zip.getArchiveName().substring(0, zip.getArchiveName().length() - zip.getExtension().length() - 1);
                CopySpecInternal baseSpec = zip.getRootSpec().addChild();
                baseSpec.into(baseDirName);
                baseSpec.with(distributions.findByName(binary.getName()).getContents());

                zip.dependsOn(binary.getTasks().withType(Jar.class));
                zip.dependsOn(binary.getTasks().withType(CreateStartScripts.class));
            }
        });
    }
}
