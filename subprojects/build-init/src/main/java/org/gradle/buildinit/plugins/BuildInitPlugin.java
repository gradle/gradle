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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.specs.Spec;
import org.gradle.buildinit.InsecureProtocolOption;
import org.gradle.buildinit.tasks.InitBuild;
import org.gradle.internal.file.RelativeFilePathResolver;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * The build init plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/build_init_plugin.html">Build Init plugin reference</a>
 */
public class BuildInitPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project.getParent() == null) {
            project.getTasks().register("init", InitBuild.class, initBuild -> {
                initBuild.setGroup("Build Setup");
                initBuild.setDescription("Initializes a new Gradle build.");

                RelativeFilePathResolver resolver = ((ProjectInternal) project).getFileResolver();
                File buildFile = project.getBuildFile();
                FileDetails buildFileDetails = FileDetails.of(buildFile, resolver);
                File settingsFile = ((ProjectInternal) project).getGradle().getSettings().getSettingsScript().getResource().getLocation().getFile();
                FileDetails settingsFileDetails = FileDetails.of(settingsFile, resolver);

                initBuild.onlyIf(new InitBuildOnlyIfSpec(buildFileDetails, settingsFileDetails, initBuild.getLogger()));
                initBuild.dependsOn(new InitBuildDependsOnCallable(buildFileDetails, settingsFileDetails));

                ProjectInternal.DetachedResolver detachedResolver = ((ProjectInternal) project).newDetachedResolver();
                initBuild.getProjectLayoutRegistry().getBuildConverter().configureClasspath(detachedResolver, project.getObjects());

                initBuild.getInsecureProtocol().convention(InsecureProtocolOption.WARN);
            });
        }
    }

    private static class InitBuildOnlyIfSpec implements Spec<Task> {

        private final FileDetails buildFile;
        private final FileDetails settingsFile;
        private final Logger logger;

        private InitBuildOnlyIfSpec(FileDetails buildFile, FileDetails settingsFile, Logger logger) {
            this.buildFile = buildFile;
            this.settingsFile = settingsFile;
            this.logger = logger;
        }

        @Override
        public boolean isSatisfiedBy(Task element) {
            String skippedMsg = reasonToSkip(buildFile, settingsFile);
            if (skippedMsg != null) {
                logger.warn(skippedMsg);
                return false;
            }
            return true;
        }
    }

    private static class InitBuildDependsOnCallable implements Callable<String> {

        private final FileDetails buildFile;
        private final FileDetails settingsFile;

        private InitBuildDependsOnCallable(FileDetails buildFile, FileDetails settingsFile) {
            this.buildFile = buildFile;
            this.settingsFile = settingsFile;
        }

        @Override
        public String call() {
            if (reasonToSkip(buildFile, settingsFile) == null) {
                return "wrapper";
            } else {
                return null;
            }
        }
    }

    private static String reasonToSkip(FileDetails buildFile, FileDetails settingsFile) {
        if (buildFile != null && buildFile.file.exists()) {
            return "The build file '" + buildFile.pathForDisplay + "' already exists. Skipping build initialization.";
        }

        if (settingsFile != null && settingsFile.file.exists()) {
            return "The settings file '" + settingsFile.pathForDisplay + "' already exists. Skipping build initialization.";
        }

        return null;
    }

    private static class FileDetails {
        final File file;
        final String pathForDisplay;

        public FileDetails(File file, String pathForDisplay) {
            this.file = file;
            this.pathForDisplay = pathForDisplay;
        }

        @Nullable
        public static FileDetails of(@Nullable File file, RelativeFilePathResolver resolver) {
            if (file == null) {
                return null;
            }
            return new FileDetails(file, resolver.resolveForDisplay(file));
        }
    }
}
