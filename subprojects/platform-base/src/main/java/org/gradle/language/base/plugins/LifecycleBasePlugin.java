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

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.Delete;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.language.base.internal.plugins.CleanRule;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle.</p>
 */
@Incubating
public class LifecycleBasePlugin implements Plugin<ProjectInternal> {
    public static final String CLEAN_TASK_NAME = "clean";
    public static final String ASSEMBLE_TASK_NAME = "assemble";
    public static final String CHECK_TASK_NAME = "check";
    public static final String BUILD_TASK_NAME = "build";
    public static final String BUILD_GROUP = "build";
    public static final String VERIFICATION_GROUP = "verification";

    private static final String CUSTOM_LIFECYCLE_TASK_ERROR_MSG = "Declaring custom '%s' task when using the standard Gradle lifecycle plugins is not allowed.";
    private final Set<String> placeholders = new HashSet<String>();

    @Override
    public void apply(final ProjectInternal project) {
        addClean(project);
        addCleanRule(project);
        addAssemble(project);
        addCheck(project);
        addBuild(project);
        addDeprecationWarningsAboutCustomLifecycleTasks(project);
    }

    private void addClean(final ProjectInternal project) {
        final Callable<File> buildDir = new Callable<File>() {
            public File call() throws Exception {
                return project.getBuildDir();
            }
        };

        // Register at least the project buildDir as a directory to be deleted.
        final BuildOutputCleanupRegistry buildOutputCleanupRegistry = project.getServices().get(BuildOutputCleanupRegistry.class);
        buildOutputCleanupRegistry.registerOutputs(buildDir);

        addPlaceholderAction(project, CLEAN_TASK_NAME, Delete.class, new Action<Delete>() {
            @Override
            public void execute(final Delete clean) {
                clean.setDescription("Deletes the build directory.");
                clean.setGroup(BUILD_GROUP);
                clean.delete(buildDir);
                buildOutputCleanupRegistry.registerOutputs(new Callable<FileCollection>() {
                    @Override
                    public FileCollection call() throws Exception {
                        return clean.getTargetFiles();
                    }
                });
            }
        });
    }

    private void addCleanRule(Project project) {
        project.getTasks().addRule(new CleanRule(project.getTasks()));
    }

    private void addAssemble(ProjectInternal project) {
        addPlaceholderAction(project, ASSEMBLE_TASK_NAME, DefaultTask.class, new Action<TaskInternal>() {
            @Override
            public void execute(TaskInternal assembleTask) {
                assembleTask.setDescription("Assembles the outputs of this project.");
                assembleTask.setGroup(BUILD_GROUP);
            }
        });
    }

    private void addCheck(ProjectInternal project) {
        addPlaceholderAction(project, CHECK_TASK_NAME, DefaultTask.class, new Action<TaskInternal>() {
            @Override
            public void execute(TaskInternal checkTask) {
                checkTask.setDescription("Runs all checks.");
                checkTask.setGroup(VERIFICATION_GROUP);
            }
        });
    }

    private void addBuild(final ProjectInternal project) {
        addPlaceholderAction(project, BUILD_TASK_NAME, DefaultTask.class, new Action<DefaultTask>() {
            @Override
            public void execute(DefaultTask buildTask) {
                buildTask.setDescription("Assembles and tests this project.");
                buildTask.setGroup(BUILD_GROUP);
                buildTask.dependsOn(ASSEMBLE_TASK_NAME);
                buildTask.dependsOn(CHECK_TASK_NAME);
            }
        });
    }

    <T extends TaskInternal> void addPlaceholderAction(ProjectInternal project, final String placeholderName, Class<T> type, final Action<? super T> configure) {
        placeholders.add(placeholderName);
        project.getTasks().addPlaceholderAction(placeholderName, type, new Action<T>() {
            @Override
            public void execute(T t) {
                t.getExtensions().getExtraProperties().set("placeholder", true);
                configure.execute(t);
            }
        });
    }

    private void addDeprecationWarningsAboutCustomLifecycleTasks(ProjectInternal project) {
        project.getTasks().all(new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (placeholders.contains(task.getName()) && !task.getExtensions().getExtraProperties().has("placeholder")) {
                    throw new InvalidUserDataException(String.format(CUSTOM_LIFECYCLE_TASK_ERROR_MSG, task.getName()));
                }
            }
        });
    }
}
