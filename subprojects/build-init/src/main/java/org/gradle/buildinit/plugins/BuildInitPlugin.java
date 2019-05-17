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

package org.gradle.buildinit.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.tasks.InitBuild;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * The build init plugin.
 */
public class BuildInitPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        if (project.getParent() == null) {
            project.getTasks().register("init", InitBuild.class, new Action<InitBuild>() {
                @Override
                public void execute(InitBuild initBuild) {
                    initBuild.setGroup("Build Setup");
                    initBuild.setDescription("Initializes a new Gradle build.");

                    initBuild.onlyIf(new Spec<Task>() {
                        @Override
                        public boolean isSatisfiedBy(Task element) {
                            Object skippedMsg = reasonToSkip(project);
                            if (skippedMsg != null) {
                                project.getLogger().warn((String) skippedMsg);
                                return false;
                            }

                            return true;
                        }
                    });

                    initBuild.dependsOn(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            if (reasonToSkip(project) == null) {
                                return "wrapper";
                            } else {
                                return null;
                            }
                        }
                    });
                }
            });
        }
    }

    private String reasonToSkip(Project project) {
        for (BuildInitDsl dsl : BuildInitDsl.values()) {
            String buildFileName = dsl.fileNameFor("build");
            if (project.file(buildFileName).exists()) {
                return "The build file '" + buildFileName + "' already exists. Skipping build initialization.";
            }
            String settingsFileName = dsl.fileNameFor("settings");
            if (project.file(settingsFileName).exists()) {
                return "The settings file '" + settingsFileName + "' already exists. Skipping build initialization.";
            }
        }

        File buildFile = project.getBuildFile();
        if (buildFile != null && buildFile.exists()) {
            return "The build file \'" + buildFile.getName() + "\' already exists. Skipping build initialization.";
        }

        if (project.getSubprojects().size() > 0) {
            return "This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.";
        }

        return null;
    }
}
