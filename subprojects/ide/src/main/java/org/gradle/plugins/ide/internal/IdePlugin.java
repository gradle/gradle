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
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.plugins.ide.api.GeneratorTask;
import org.gradle.process.ExecSpec;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

public abstract class IdePlugin implements Plugin<Project> {
    private Task lifecycleTask;
    private Task cleanTask;
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
        lifecycleTask = target.task(lifecycleTaskName);
        lifecycleTask.setGroup("IDE");
        cleanTask = target.getTasks().create(cleanName(lifecycleTaskName), Delete.class);
        cleanTask.setGroup("IDE");
        onApply(target);
    }

    public Task getLifecycleTask() {
        return lifecycleTask;
    }

    public Task getCleanTask() {
        return cleanTask;
    }

    public Task getCleanTask(Task worker) {
        return project.getTasks().getByName(cleanName(worker.getName()));
    }

    protected String cleanName(String taskName) {
        return String.format("clean%s", StringUtils.capitalize(taskName));
    }

    public void addWorker(Task worker) {
        addWorker(worker, true);
    }

    public void addWorker(Task worker, boolean includeInClean) {
        lifecycleTask.dependsOn(worker);
        Delete cleanWorker = project.getTasks().create(cleanName(worker.getName()), Delete.class);
        cleanWorker.delete(worker.getOutputs().getFiles());
        if (includeInClean) {
            cleanTask.dependsOn(cleanWorker);
        }
    }

    protected void onApply(Project target) {
    }

    protected Task addWorkspaceOpenTask(final GeneratorTask<?> task) {
        return addWorkspaceOpenTask(project.getProviders().provider(new Callable<File>() {
            @Override
            public File call() {
                return task.getOutputFile();
            }
        }));
    }

    protected Task addWorkspaceOpenTask(final Provider<? extends File> workspace) {
        Task openTask = project.getTasks().create("open" + StringUtils.capitalize(getLifecycleTaskName()));
        openTask.dependsOn(lifecycleTask);
        openTask.setGroup("IDE");
        openTask.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                if (OperatingSystem.current().isMacOsX()) {
                    project.exec(new Action<ExecSpec>() {
                        @Override
                        public void execute(ExecSpec execSpec) {
                            execSpec.commandLine("open", workspace.get());
                        }
                    });
                } else {
                    try {
                        Desktop.getDesktop().open(workspace.get());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        });
        return openTask;
    }

    protected abstract String getLifecycleTaskName();

    public boolean isRoot() {
        return project.getParent() == null;
    }
}
