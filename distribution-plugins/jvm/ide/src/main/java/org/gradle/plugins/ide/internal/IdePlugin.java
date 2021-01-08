/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal;

import com.google.common.base.Optional;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.ide.IdeWorkspace;
import org.gradle.process.ExecSpec;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public abstract class IdePlugin implements Plugin<Project> {
    private static final Logger LOGGER = Logging.getLogger(IdePlugin.class);

    private TaskProvider<Task> lifecycleTask;
    private TaskProvider<Delete> cleanTask;
    protected Project project;

    /**
     * Returns the path to the correct Gradle distribution to use. The wrapper of the generating project will be used only if the execution context of the currently running Gradle is in the Gradle home (typical of a wrapper execution context). If this isn't the case, we try to use the current Gradle home, if available, as the distribution. Finally, if nothing matches, we default to the system-wide Gradle distribution.
     *
     * @param project the Gradle project generating the IDE files
     * @return path to Gradle distribution to use within the generated IDE files
     */
    public static String toGradleCommand(Project project) {
        Gradle gradle = project.getGradle();
        Optional<String> gradleWrapperPath = Optional.absent();

        Project rootProject = project.getRootProject();
        String gradlewExtension = OperatingSystem.current().isWindows() ? ".bat" : "";
        File gradlewFile = rootProject.file("gradlew" + gradlewExtension);
        if (gradlewFile.exists()) {
            gradleWrapperPath = Optional.of(gradlewFile.getAbsolutePath());
        }

        if (gradle.getGradleHomeDir() != null) {
            if (gradleWrapperPath.isPresent() && gradle.getGradleHomeDir().getAbsolutePath().startsWith(gradle.getGradleUserHomeDir().getAbsolutePath())) {
                return gradleWrapperPath.get();
            }
            return gradle.getGradleHomeDir().getAbsolutePath() + "/bin/gradle";
        }

        return gradleWrapperPath.or("gradle");
    }

    @Override
    public void apply(Project target) {
        project = target;
        String lifecycleTaskName = getLifecycleTaskName();
        lifecycleTask = target.getTasks().register(lifecycleTaskName);
        cleanTask = target.getTasks().register(cleanName(lifecycleTaskName), Delete.class, new Action<Delete>() {
            @Override
            public void execute(Delete task) {
                task.setGroup("IDE");
            }
        });
        lifecycleTask.configure(new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setGroup("IDE");
                task.shouldRunAfter(cleanTask);
            }
        });
        onApply(target);
    }

    public TaskProvider<? extends Task> getLifecycleTask() {
        return lifecycleTask;
    }

    public TaskProvider<? extends Task> getCleanTask() {
        return cleanTask;
    }

    protected String cleanName(String taskName) {
        return String.format("clean%s", StringUtils.capitalize(taskName));
    }

    public void addWorker(Task worker) {
        addWorker(project.getTasks().named(worker.getName()), worker.getName());
    }

    public void addWorker(TaskProvider<? extends Task> worker, String workerName) {
        addWorker(worker, workerName, true);
    }

    public void addWorker(Task worker, boolean includeInClean) {
        addWorker(project.getTasks().named(worker.getName()), worker.getName(), includeInClean);
    }

    public void addWorker(final TaskProvider<? extends Task> worker, String workerName, boolean includeInClean) {
        lifecycleTask.configure(dependsOn(worker));
        final TaskProvider<Delete> cleanWorker = project.getTasks().register(cleanName(workerName), Delete.class, new Action<Delete>() {
            @Override
            public void execute(Delete cleanWorker) {
                cleanWorker.delete(worker);
            }
        });

        if (includeInClean) {
            cleanTask.configure(dependsOn(cleanWorker));
        }

        // Always schedule the generation task after the clean task
        worker.configure(new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.shouldRunAfter(cleanWorker);
            }
        });
    }

    protected static Action<? super Task> dependsOn(final Task taskDependency) {
        return new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(taskDependency);
            }
        };
    }

    protected static Action<? super Task> dependsOn(final TaskProvider<? extends Task> taskProvider) {
        return new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(taskProvider);
            }
        };
    }

    protected static Action<? super Task> withDescription(final String description) {
        return new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription(description);
            }
        };
    }

    protected void onApply(Project target) {
    }

    protected void addWorkspace(final IdeWorkspace workspace) {
        lifecycleTask.configure(new Action<Task>() {
            @Override
            public void execute(Task lifecycleTask) {
                lifecycleTask.doLast(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        LOGGER.lifecycle(String.format("Generated %s at %s", workspace.getDisplayName(), new ConsoleRenderer().asClickableFileUrl(workspace.getLocation().get().getAsFile())));
                    }
                });
            }
        });

        project.getTasks().register("open" + StringUtils.capitalize(getLifecycleTaskName()), new Action<Task>() {
            @Override
            public void execute(Task openTask) {
                openTask.dependsOn(lifecycleTask);
                openTask.setGroup("IDE");
                openTask.setDescription("Opens the " + workspace.getDisplayName());
                openTask.doLast(new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        if (OperatingSystem.current().isMacOsX()) {
                            project.exec(new Action<ExecSpec>() {
                                @Override
                                public void execute(ExecSpec execSpec) {
                                    execSpec.commandLine("open", workspace.getLocation().get());
                                }
                            });
                        } else {
                            try {
                                Desktop.getDesktop().open(workspace.getLocation().get().getAsFile());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    }
                });
            }
        });
    }

    protected abstract String getLifecycleTaskName();

    public boolean isRoot() {
        return project.getParent() == null;
    }
}
