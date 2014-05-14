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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;
import org.gradle.language.base.internal.plugins.CleanRule;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@link org.gradle.api.Plugin} which defines a basic project lifecycle.</p>
 */
@Incubating
public class LifecycleBasePlugin implements Plugin<Project> {
    public static final String CLEAN_TASK_NAME = "clean";
    public static final String ASSEMBLE_TASK_NAME = "assemble";
    public static final String BUILD_GROUP = "build";

    public void apply(Project project) {
        addClean(project);
        addCleanRule(project);
        addAssemble(project);
    }

    private void addClean(final Project project) {
        Delete clean = project.getTasks().create(CLEAN_TASK_NAME, Delete.class);
        clean.setDescription("Deletes the build directory.");
        clean.setGroup(BUILD_GROUP);
        clean.delete(new Callable<File>() {
            public File call() throws Exception {
                return project.getBuildDir();
            }
        });
    }

    private void addCleanRule(Project project) {
        project.getTasks().addRule(new CleanRule(project.getTasks()));
    }

    private void addAssemble(Project project) {
        Task assembleTask = project.getTasks().create(ASSEMBLE_TASK_NAME);
        assembleTask.setDescription("Assembles the outputs of this project.");
        assembleTask.setGroup(BUILD_GROUP);
    }
}
