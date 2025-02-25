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
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.internal.DefaultDistributionContainer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
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
import org.gradle.util.internal.TextUtil;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * A plugin that configures rules allowing projects to be packaged as a distribution.
 * <p>
 * As a base plugin, this plugin adds no distributions by default.
 * The {@link DistributionPlugin} adds a {@code main} distribution as a convention.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/distribution_plugin.html">Distribution plugin reference</a>
 *
 * @since 8.13
 */
public abstract class DistributionBasePlugin implements Plugin<Project> {

    private static final String DISTRIBUTION_GROUP = "distribution";
    private static final String TASK_DIST_ZIP_NAME = "distZip";
    private static final String TASK_DIST_TAR_NAME = "distTar";
    private static final String TASK_ASSEMBLE_NAME = "assembleDist";

    private final Instantiator instantiator;
    private final FileOperations fileOperations;
    private final CollectionCallbackActionDecorator callbackActionDecorator;

    @Inject
    public DistributionBasePlugin(Instantiator instantiator, FileOperations fileOperations, CollectionCallbackActionDecorator callbackActionDecorator) {
        this.instantiator = instantiator;
        this.fileOperations = fileOperations;
        this.callbackActionDecorator = callbackActionDecorator;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        DefaultArtifactPublicationSet defaultArtifactPublicationSet = project.getExtensions().getByType(DefaultArtifactPublicationSet.class);

        DistributionContainer distributions = project.getExtensions().create(DistributionContainer.class, "distributions", DefaultDistributionContainer.class, Distribution.class, instantiator, project.getObjects(), fileOperations, callbackActionDecorator);
        distributions.all(dist -> configureDistribution((ProjectInternal) project, dist, defaultArtifactPublicationSet));

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

    /**
     * Configures conventions and associated domain objects for a single distribution.
     */
    private static void configureDistribution(
        ProjectInternal project,
        Distribution dist,
        DefaultArtifactPublicationSet defaultArtifactPublicationSet
    ) {
        dist.getContents().from("src/" + dist.getName() + "/dist");

        String zipTaskName;
        String tarTaskName;
        String installTaskName;
        String assembleTaskName;

        if (dist.getName().equals(DistributionPlugin.MAIN_DISTRIBUTION_NAME)) {
            zipTaskName = TASK_DIST_ZIP_NAME;
            tarTaskName = TASK_DIST_TAR_NAME;
            installTaskName = DistributionPlugin.TASK_INSTALL_NAME;
            assembleTaskName = TASK_ASSEMBLE_NAME;
            dist.getDistributionBaseName().convention(project.getName());
        } else {
            zipTaskName = dist.getName() + "DistZip";
            tarTaskName = dist.getName() + "DistTar";
            installTaskName = "install" + StringUtils.capitalize(dist.getName()) + "Dist";
            assembleTaskName = "assemble" + StringUtils.capitalize(dist.getName()) + "Dist";
            dist.getDistributionBaseName().convention(String.format("%s-%s", project.getName(), dist.getName()));
        }

        TaskProvider<Zip> zipTask = addArchiveTask(project, zipTaskName, Zip.class, dist);
        TaskProvider<Tar> tarTask = addArchiveTask(project, tarTaskName, Tar.class, dist);
        addInstallTask(project, installTaskName, dist);
        addAssembleTask(project, dist, assembleTaskName, zipTask, tarTask);

        // Build zips and tars by default when running the build-wide assemble task.
        defaultArtifactPublicationSet.addCandidate(new LazyPublishArtifact(zipTask, project.getFileResolver(), project.getTaskDependencyFactory()));
        defaultArtifactPublicationSet.addCandidate(new LazyPublishArtifact(tarTask, project.getFileResolver(), project.getTaskDependencyFactory()));
    }

    /**
     * Adds a task that archives the contents of the distribution into an archive file.
     */
    private static <T extends AbstractArchiveTask> TaskProvider<T> addArchiveTask(
        Project project,
        String taskName,
        Class<T> type,
        Distribution distribution
    ) {
        return project.getTasks().register(taskName, type, task -> {
            task.setDescription("Bundles the project as a distribution.");
            task.setGroup(DISTRIBUTION_GROUP);
            task.getArchiveBaseName().convention(distribution.getDistributionBaseName());
            task.getArchiveClassifier().convention(distribution.getDistributionClassifier());

            CopySpec childSpec = project.copySpec();
            childSpec.with(distribution.getContents());
            childSpec.into((Callable<String>) () ->
                TextUtil.minus(task.getArchiveFileName().get(), "." + task.getArchiveExtension().get())
            );
            task.with(childSpec);
        });
    }

    /**
     * Adds a task that syncs the contents of the distribution into a directory within the build dir.
     */
    private static void addInstallTask(Project project, String taskName, Distribution distribution) {
        project.getTasks().register(taskName, Sync.class, installTask -> {
            installTask.setDescription("Installs the project as a distribution as-is.");
            installTask.setGroup(DISTRIBUTION_GROUP);
            installTask.with(distribution.getContents());
            Provider<String> installDirectoryName = project.provider(() -> {
                String baseName = distribution.getDistributionBaseName().get();
                String classifier = distribution.getDistributionClassifier().getOrNull();
                return "install/" + baseName + (classifier != null ? "-" + classifier : "");
            });
            installTask.into(project.getLayout().getBuildDirectory().dir(installDirectoryName));
        });
    }

    /**
     * Adds a task that builds all archives for distribution.
     */
    private static void addAssembleTask(
        ProjectInternal project,
        Distribution dist,
        String assembleTaskName,
        TaskProvider<Zip> zipTask,
        TaskProvider<Tar> tarTask
    ) {
        project.getTasks().register(assembleTaskName, DefaultTask.class, assembleTask -> {
            assembleTask.setDescription("Assembles the " + dist.getName() + " distributions");
            assembleTask.setGroup(DISTRIBUTION_GROUP);
            assembleTask.dependsOn(zipTask);
            assembleTask.dependsOn(tarTask);
        });
    }
}
