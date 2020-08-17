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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.Logger;
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
    public void apply(Project project) {
        if (project.getParent() == null) {
            project.getTasks().register("init", InitBuild.class, initBuild -> {

                initBuild.setGroup("Build Setup");
                initBuild.setDescription("Initializes a new Gradle build.");

                File buildFile = project.getBuildFile();
                boolean hasSubProjects = !project.getSubprojects().isEmpty();

                initBuild.onlyIf(new InitBuildOnlyIfSpec(buildFile, hasSubProjects, project.getLayout(), initBuild.getLogger()));
                initBuild.dependsOn(new InitBuildDependsOnCallable(buildFile, hasSubProjects, project.getLayout()));
            });
        }
    }

    private static class InitBuildOnlyIfSpec implements Spec<Task> {

        private final File buildFile;
        private final boolean hasSubProjects;
        private final ProjectLayout layout;
        private final Logger logger;

        private InitBuildOnlyIfSpec(File buildFile, boolean hasSubProjects, ProjectLayout layout, Logger logger) {
            this.buildFile = buildFile;
            this.hasSubProjects = hasSubProjects;
            this.layout = layout;
            this.logger = logger;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            String skippedMsg = reasonToSkip(buildFile, layout, hasSubProjects);
            if (skippedMsg != null) {
                logger.warn(skippedMsg);
                return false;
            }
            return true;
        }
    }

    private static class InitBuildDependsOnCallable implements Callable<String> {

        private final File buildFile;
        private final boolean hasSubProjects;
        private final ProjectLayout layout;

        private InitBuildDependsOnCallable(File buildFile, boolean hasSubProjects, ProjectLayout layout) {
            this.buildFile = buildFile;
            this.hasSubProjects = hasSubProjects;
            this.layout = layout;
        }

        @Override
        public String call() {
            if (reasonToSkip(buildFile, layout, hasSubProjects) == null) {
                return "wrapper";
            } else {
                return null;
            }
        }
    }

    private static String reasonToSkip(File buildFile, ProjectLayout layout, boolean hasSubProjects) {
        for (BuildInitDsl dsl : BuildInitDsl.values()) {
            String buildFileName = dsl.fileNameFor("build");
            if (layout.getProjectDirectory().file(buildFileName).getAsFile().exists()) {
                return "The build file '" + buildFileName + "' already exists. Skipping build initialization.";
            }
            String settingsFileName = dsl.fileNameFor("settings");
            if (layout.getProjectDirectory().file(settingsFileName).getAsFile().exists()) {
                return "The settings file '" + settingsFileName + "' already exists. Skipping build initialization.";
            }
        }

        if (buildFile != null && buildFile.exists()) {
            return "The build file '" + buildFile.getName() + "' already exists. Skipping build initialization.";
        }

        if (hasSubProjects) {
            return "This Gradle project appears to be part of an existing multi-project Gradle build. Skipping build initialization.";
        }

        return null;
    }
}
