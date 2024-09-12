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

package org.gradle.language.base.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.lifecycle.LifecycleExtension;
import org.gradle.api.lifecycle.LifecycleStage;
import org.gradle.api.lifecycle.internal.DefaultLifecycleExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.language.base.internal.plugins.CleanRule;

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html">Base plugin reference</a>
 */
public abstract class LifecycleBasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = "clean";
    public static final String ASSEMBLE = "assemble";
    public static final String ASSEMBLE_TASK_NAME = ASSEMBLE;
    public static final String CHECK = "check";
    public static final String CHECK_TASK_NAME = CHECK;
    public static final String BUILD = "build";
    public static final String BUILD_TASK_NAME = BUILD;
    public static final String BUILD_GROUP = "build";
    public static final String VERIFICATION_GROUP = "verification";
    public static final String LIFECYCLE_EXTENSION = "lifecycle";

    @Override
    public void apply(final Project project) {
        final ProjectInternal projectInternal = (ProjectInternal) project;
        LifecycleExtension extension = addLifecycleStages(project);
        addClean(projectInternal);
        addCleanRule(project);
        addAssemble(project, extension);
        addCheck(project, extension);
        addBuild(project, extension);
    }

    private LifecycleExtension addLifecycleStages(Project project) {
        LifecycleExtension extension = project.getExtensions().create(LifecycleExtension.class, LIFECYCLE_EXTENSION, DefaultLifecycleExtension.class);
        LifecycleStage assemble = extension.getStages().create(ASSEMBLE);
        LifecycleStage check = extension.getStages().create(CHECK);
        LifecycleStage build = extension.getStages().create(BUILD);
        build.getMembers().add(assemble);
        build.getMembers().add(check);
        return extension;
    }

    private void addClean(final ProjectInternal project) {
        Provider<Directory> buildDir = project.getLayout().getBuildDirectory();

        // Register at least the project buildDir as a directory to be deleted.
        final BuildOutputCleanupRegistry buildOutputCleanupRegistry = project.getServices().get(BuildOutputCleanupRegistry.class);
        buildOutputCleanupRegistry.registerOutputs(buildDir);

        final Provider<Delete> clean = project.getTasks().register(CLEAN_TASK_NAME, Delete.class, cleanTask -> {
            cleanTask.setDescription("Deletes the build directory.");
            cleanTask.setGroup(BUILD_GROUP);
            cleanTask.delete(buildDir);
        });
        buildOutputCleanupRegistry.registerOutputs(clean.map(cl -> cl.getTargetFiles()));
    }

    private void addCleanRule(Project project) {
        project.getTasks().addRule(new CleanRule(project.getTasks()));
    }

    private void addAssemble(Project project, LifecycleExtension extension) {
        LifecycleStage assemble = extension.getStages().getByName(ASSEMBLE);
        project.getTasks().register(ASSEMBLE_TASK_NAME, assembleTask -> {
            assembleTask.setDescription("Assembles the outputs of this project.");
            assembleTask.setGroup(BUILD_GROUP);
            assembleTask.dependsOn(assemble.getAllOutputs());
        });
    }

    private void addCheck(Project project, LifecycleExtension extension) {
        LifecycleStage check = extension.getStages().getByName(CHECK);
        project.getTasks().register(CHECK_TASK_NAME, checkTask -> {
            checkTask.setDescription("Runs all checks.");
            checkTask.setGroup(VERIFICATION_GROUP);
            checkTask.dependsOn(check.getAllOutputs());
        });
    }

    private void addBuild(final Project project, LifecycleExtension extension) {
        LifecycleStage build = extension.getStages().getByName(BUILD);
        project.getTasks().register(BUILD_TASK_NAME, buildTask -> {
            buildTask.setDescription("Assembles and tests this project.");
            buildTask.setGroup(BUILD_GROUP);

            // This is only so that the assemble and check tasks actually execute when the build task is run.
            // The build lifecycle already knows about assemble and check, so this is technically not necessary in
            // order to produce the lifecycle outputs for assemble and check.  This is only here for backwards
            // compatibility.
            buildTask.dependsOn(ASSEMBLE_TASK_NAME);
            buildTask.dependsOn(CHECK_TASK_NAME);
            buildTask.dependsOn(build.getAllOutputs());
        });
    }
}
