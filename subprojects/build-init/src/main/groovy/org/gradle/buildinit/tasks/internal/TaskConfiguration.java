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

package org.gradle.buildinit.tasks.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.buildinit.tasks.InitBuild;

import java.io.File;
import java.util.concurrent.Callable;

public class TaskConfiguration {

    public static final String INIT_BUILD_TASK_NAME = "init";
    public static final String GROUP = "Build Setup";

    public static void configureInit(final InitBuild init) {
        init.setGroup(GROUP);
        init.setDescription("Initializes a new Gradle build.");
        final Transformer<String, Project> setupCanBeSkipped = new Transformer<String, Project>() {

            @Override
            public String transform(Project project) {
                if (project.file("build.gradle").exists()) {
                    return "The build file 'build.gradle' already exists. Skipping build initialization.";
                }

                File buildFile = project.getBuildFile();
                if (buildFile != null && buildFile.exists()) {
                    return "The build file \'" + buildFile.getName() + "\' already exists. Skipping build initialization.";
                }

                if (project.file("settings.gradle").exists()) {
                    return "The settings file 'settings.gradle' already exists. Skipping build initialization.";
                }

                if (project.getSubprojects().size() > 0) {
                    return "This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.";
                }

                return null;
            }
        };
        init.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                Object skippedMsg = setupCanBeSkipped.transform(element.getProject());
                if (skippedMsg != null) {
                    element.getProject().getLogger().warn((String) skippedMsg);
                    return false;
                }

                return true;
            }
        });

        init.dependsOn(new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (setupCanBeSkipped.transform(init.getProject()) == null) {
                    return "wrapper";
                } else {
                    return null;
                }
            }
        });
    }

    public static void configureWrapper(Wrapper wrapper) {
        wrapper.setGroup(GROUP);
        wrapper.setDescription("Generates Gradle wrapper files.");
    }

    public static void createInitTask(Project project) {
        configureInit(project.getTasks().create(INIT_BUILD_TASK_NAME, InitBuild.class));
    }

    public static void createWrapperTask(Project project) {
        configureWrapper(project.getTasks().create("wrapper", Wrapper.class));
    }

    public static void addInitPlaceholder(final ProjectInternal projectInternal) {
        if (projectInternal.getParent() == null) {
            projectInternal.getTasks().addPlaceholderAction("init", InitBuild.class, new InitBuildAction());
        }
    }

    public static void addWrapperPlaceholder(ProjectInternal projectInternal) {
        if (projectInternal.getParent() == null) {
            projectInternal.getTasks().addPlaceholderAction("wrapper", Wrapper.class, new WrapperAction());
        }
    }

    private static class InitBuildAction implements Action<InitBuild> {
        @Override
        public void execute(InitBuild initBuild) {
            configureInit(initBuild);
        }
    }

    private static class WrapperAction implements Action<Wrapper> {
        @Override
        public void execute(Wrapper wrapper) {
            configureWrapper(wrapper);
        }
    }
}
