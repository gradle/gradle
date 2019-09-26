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
import org.gradle.api.Action;
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
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.TextUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} to package project as a distribution.</p>
 */
public class DistributionPlugin implements Plugin<ProjectInternal> {
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
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(BasePlugin.class);
        DistributionContainer distributions = project.getExtensions().create(DistributionContainer.class, "distributions", DefaultDistributionContainer.class, Distribution.class, instantiator, fileOperations, callbackActionDecorator);

        // TODO - refactor this action out so it can be unit tested
        distributions.all(dist -> {
            ((IConventionAware) dist).getConventionMapping().map("baseName", () -> dist.getName().equals(MAIN_DISTRIBUTION_NAME) ? project.getName() : String.format("%s-%s", project.getName(), dist.getName()));

            dist.getContents().from("src/" + dist.getName() + "/dist");
            String zipTaskName = MAIN_DISTRIBUTION_NAME.equals(dist.getName()) ? TASK_DIST_ZIP_NAME : dist.getName() + "DistZip";
            TaskProvider<Zip> zipTask = configureArchiveTask(project, zipTaskName, dist, Zip.class);
            String tarTaskName = MAIN_DISTRIBUTION_NAME.equals(dist.getName()) ? TASK_DIST_TAR_NAME : dist.getName() + "DistTar";
            TaskProvider<Tar> tarTask = configureArchiveTask(project, tarTaskName, dist, Tar.class);
            addAssembleTask(project, dist, zipTask, tarTask);
            addInstallTask(project, dist);
        });
        distributions.create(MAIN_DISTRIBUTION_NAME);
    }

    private <T extends AbstractArchiveTask> TaskProvider<T> configureArchiveTask(final Project project, String taskName, final Distribution distribution, Class<T> type) {
        final TaskProvider<T> archiveTask = project.getTasks().register(taskName, type, archiveTask1 -> {
            archiveTask1.setDescription("Bundles the project as a distribution.");
            archiveTask1.setGroup(DISTRIBUTION_GROUP);
            archiveTask1.getArchiveBaseName().set(project.provider(() -> {
                if (distribution.getBaseName() == null || distribution.getBaseName().equals("")) {
                    throw new GradleException("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin.");
                }
                return distribution.getBaseName();
            }));
        });


        Provider<String> baseDir = archiveTask.map(task -> TextUtil.minus(task.getArchiveFileName().get(), "." + task.getArchiveExtension().get()));

        final CopySpec childSpec = project.copySpec();
        childSpec.into(baseDir);
        childSpec.with(distribution.getContents());
        archiveTask.configure(t -> t.with(childSpec));

        PublishArtifact archiveArtifact = new LazyPublishArtifact(archiveTask);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(archiveArtifact);
        return archiveTask;
    }

    private void addInstallTask(final Project project, final Distribution distribution) {
        String taskName = TASK_INSTALL_NAME;
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.getName())) {
            taskName = "install" + StringUtils.capitalize(distribution.getName()) + "Dist";
        }

        project.getTasks().register(taskName, Sync.class, installTask -> {
            installTask.setDescription("Installs the project as a distribution as-is.");
            installTask.setGroup(DISTRIBUTION_GROUP);
            installTask.with(distribution.getContents());

            installTask.into(project.getLayout().getBuildDirectory().dir(project.provider(() -> "/install/" + distribution.getBaseName())));
        });
    }

    private void addAssembleTask(Project project, final Distribution distribution, final TaskProvider<?>... tasks) {
        String taskName = TASK_ASSEMBLE_NAME;
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.getName())) {
            taskName = "assemble" + StringUtils.capitalize(distribution.getName()) + "Dist";
        }

        project.getTasks().register(taskName, DefaultTask.class, assembleTask -> {
            assembleTask.setDescription("Assembles the " + distribution.getName() + " distributions");
            assembleTask.setGroup(DISTRIBUTION_GROUP);
            assembleTask.dependsOn(tasks);
        });
    }
}
