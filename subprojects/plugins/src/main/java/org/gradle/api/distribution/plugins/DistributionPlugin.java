/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.distribution.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.internal.DefaultDistributionContainer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.internal.TextUtil;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} to package project as a distribution.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/distribution_plugin.html">Distribution plugin reference</a>
 */
public class DistributionPlugin implements Plugin<Project> {
    /**
     * Name of the main distribution
     */
    public static final String MAIN_DISTRIBUTION_NAME = "main";
    public static final String TASK_INSTALL_NAME = "installDist";

    private static final String DISTRIBUTION_GROUP = "distribution";
    private static final String TASK_DIST_ZIP_NAME = "distZip";
    private static final String TASK_DIST_TAR_NAME = "distTar";
    private static final String TASK_ASSEMBLE_NAME = "assembleDist";

    private final Instantiator instantiator;
    private final FileOperations fileOperations;
    private final CollectionCallbackActionDecorator callbackActionDecorator;

    @Inject
    public DistributionPlugin(Instantiator instantiator, FileOperations fileOperations, CollectionCallbackActionDecorator callbackActionDecorator) {
        this.instantiator = instantiator;
        this.fileOperations = fileOperations;
        this.callbackActionDecorator = callbackActionDecorator;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        DistributionContainer distributions = project.getExtensions().create(DistributionContainer.class, "distributions", DefaultDistributionContainer.class, Distribution.class, instantiator, project.getObjects(), fileOperations, callbackActionDecorator);

        // TODO - refactor this action out so it can be unit tested
        distributions.all(dist -> {
            dist.getContents().from("src/" + dist.getName() + "/dist");
            final String zipTaskName;
            final String tarTaskName;
            final String installTaskName;
            final String assembleTaskName;
            if (dist.getName().equals(MAIN_DISTRIBUTION_NAME)) {
                zipTaskName = TASK_DIST_ZIP_NAME;
                tarTaskName = TASK_DIST_TAR_NAME;
                installTaskName = TASK_INSTALL_NAME;
                assembleTaskName = TASK_ASSEMBLE_NAME;
                dist.getDistributionBaseName().convention(project.getName());
            } else {
                zipTaskName = dist.getName() + "DistZip";
                tarTaskName = dist.getName() + "DistTar";
                installTaskName = "install" + StringUtils.capitalize(dist.getName()) + "Dist";
                assembleTaskName = "assemble" + StringUtils.capitalize(dist.getName()) + "Dist";
                dist.getDistributionBaseName().convention(String.format("%s-%s", project.getName(), dist.getName()));
            }

            addArchiveTask(project, zipTaskName, Zip.class, dist);
            addArchiveTask(project, tarTaskName, Tar.class, dist);
            addInstallTask(project, installTaskName, dist);
            addAssembleTask(project, assembleTaskName, dist, zipTaskName, tarTaskName);
        });
        distributions.create(MAIN_DISTRIBUTION_NAME);

        // TODO: Maintain old behavior of checking for empty-string distribution base names.
        // It would be nice if we could do this as validation on the property itself.
        project.afterEvaluate(p -> {
            distributions.forEach(distribution -> {
                if (distribution.getDistributionBaseName().get().equals("")) {
                    throw new GradleException(String.format("Distribution '%s' must not have an empty distributionBaseName.", distribution.getName()));
                }
            });
        });
    }

    private <T extends AbstractArchiveTask> void addArchiveTask(final Project project, String taskName, Class<T> type, final Distribution distribution) {
        final TaskProvider<T> archiveTask = project.getTasks().register(taskName, type, task -> {
            task.setDescription("Bundles the project as a distribution.");
            task.setGroup(DISTRIBUTION_GROUP);
            task.getArchiveBaseName().convention(distribution.getDistributionBaseName());

            final CopySpec childSpec = project.copySpec();
            childSpec.with(distribution.getContents());
            childSpec.into((Callable<String>)() -> TextUtil.minus(task.getArchiveFileName().get(), "." + task.getArchiveExtension().get()));
            task.with(childSpec);
        });

        PublishArtifact archiveArtifact = new LazyPublishArtifact(archiveTask);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(archiveArtifact);
    }

    private void addInstallTask(final Project project, final String taskName, final Distribution distribution) {
        project.getTasks().register(taskName, Sync.class, installTask -> {
            installTask.setDescription("Installs the project as a distribution as-is.");
            installTask.setGroup(DISTRIBUTION_GROUP);
            installTask.with(distribution.getContents());
            installTask.into(project.getLayout().getBuildDirectory().dir(distribution.getDistributionBaseName().map(baseName -> "install/" + baseName)));
        });
    }

    private void addAssembleTask(Project project, final String taskName, final Distribution distribution, final String... tasks) {
        project.getTasks().register(taskName, DefaultTask.class, assembleTask -> {
            assembleTask.setDescription("Assembles the " + distribution.getName() + " distributions");
            assembleTask.setGroup(DISTRIBUTION_GROUP);
            assembleTask.dependsOn((Object[])tasks);
        });
    }
}
