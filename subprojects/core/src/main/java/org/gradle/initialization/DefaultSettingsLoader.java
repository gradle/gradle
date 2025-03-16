/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.initialization.buildsrc.BuildSrcDetector;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.Path;

import java.io.File;
import java.util.List;

/**
 * Handles locating and processing setting.gradle files.  Also deals with the buildSrc module, since that module is
 * found after settings is located, but needs to be built before settings is processed.
 */
public class DefaultSettingsLoader implements SettingsLoader {
    public static final String BUILD_SRC_PROJECT_PATH = ":" + SettingsInternal.BUILD_SRC;

    private final SettingsProcessor settingsProcessor;
    private final BuildLayoutFactory buildLayoutFactory;
    private final List<BuiltInCommand> builtInCommands;
    private final Logger logger;

    public DefaultSettingsLoader(
        SettingsProcessor settingsProcessor,
        BuildLayoutFactory buildLayoutFactory,
        List<BuiltInCommand> builtInCommands
    ) {
        this(settingsProcessor, buildLayoutFactory, builtInCommands, Logging.getLogger(DefaultSettingsLoader.class));
    }

    @VisibleForTesting
    /* package */ DefaultSettingsLoader(
        SettingsProcessor settingsProcessor,
        BuildLayoutFactory buildLayoutFactory,
        List<BuiltInCommand> builtInCommands,
        Logger logger
    ) {
        this.settingsProcessor = settingsProcessor;
        this.buildLayoutFactory = buildLayoutFactory;
        this.builtInCommands = builtInCommands;
        this.logger = logger;
    }

    @Override
    public SettingsState findAndLoadSettings(GradleInternal gradle) {
        StartParameter startParameter = gradle.getStartParameter();
        SettingsLocation settingsLocation = buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(startParameter));

        SettingsState state;
        ProjectSpec spec;
        if (shouldSkipLoadingBuildDefinition(startParameter)) {
            logger.debug("Skipping loading of build definition for build: '{}'", gradle.getIdentityPath());
            state = createEmptySettings(gradle, startParameter, gradle.getClassLoaderScope());
            spec = ProjectSpecs.forStartParameter(startParameter, state.getSettings());
        } else {
            logger.debug("Loading build definition for build: '{}'", gradle.getIdentityPath());
            state = findSettingsAndLoadIfAppropriate(gradle, startParameter, settingsLocation, gradle.getClassLoaderScope());
            SettingsInternal settings = state.getSettings();
            spec = ProjectSpecs.forStartParameter(startParameter, settings);
            if (useEmptySettings(spec, settings, startParameter)) {
                // Discard the loaded settings and replace with an empty one
                logger.debug("Discarding loaded settings and replacing with empty settings for build: '{}'", gradle.getIdentityPath());
                state.close();
                state = createEmptySettings(gradle, startParameter, settings.getClassLoaderScope());
            }
        }

        setDefaultProject(spec, state.getSettings());
        return state;
    }

    /**
     * Checks whether the Gradle invocation contains a built-in command that runs in a directory not contained in the settings file,
     * and shouldn't require loading the settings - it should use a new, empty Settings instance.
     *
     * return {@code true} if so; {@code false} otherwise
     */
    private boolean shouldSkipLoadingBuildDefinition(StartParameter startParameter) {
        for (BuiltInCommand command : builtInCommands) {
            if (command.requireEmptyBuildDefinition() && command.wasInvoked(startParameter)) {
                return true;
            }
        }
        return false;
    }

    private boolean useEmptySettings(ProjectSpec spec, SettingsInternal loadedSettings, StartParameter startParameter) {
        // Never use empty settings when the settings were explicitly set
        @SuppressWarnings("deprecation")
        File customSettingsFile = DeprecationLogger.whileDisabled(startParameter::getSettingsFile);
        if (customSettingsFile != null) {
            return false;
        }

        // Use the loaded settings if it includes the target project (based on build file, project dir or current dir)
        if (spec.containsProject(loadedSettings.getProjectRegistry())) {
            return false;
        }

        // Allow a built-in command to run in a directory not contained in the settings file (but don't use the settings from that file)
        for (BuiltInCommand command : builtInCommands) {
            if (command.wasInvoked(startParameter)) {
                // Allow built-in command to run in a directory not contained in the settings file (but don't use the settings from that file)
                return true;
            }
        }

        // Allow a buildSrc directory to have no settings file
        if (startParameter.getProjectDir() != null && startParameter.getProjectDir().getName().equals(SettingsInternal.BUILD_SRC) && BuildSrcDetector.isValidBuildSrcBuild(startParameter.getProjectDir())) {
            return true;
        }

        // Use an empty settings for a target build file located in the same directory as the settings file.
        return startParameter.getProjectDir() != null && loadedSettings.getSettingsDir().equals(startParameter.getProjectDir());
    }

    @SuppressWarnings("deprecation") // StartParameter.setSettingsFile() and StartParameter.getBuildFile()
    private SettingsState createEmptySettings(GradleInternal gradle, StartParameter startParameter, ClassLoaderScope classLoaderScope) {
        logger.debug("Creating empty settings for build: '{}'", gradle.getIdentityPath());
        StartParameterInternal noSearchParameter = (StartParameterInternal) startParameter.newInstance();
        DeprecationLogger.whileDisabled(() ->
            noSearchParameter.setSettingsFile(null)
        );
        noSearchParameter.useEmptySettings();
        noSearchParameter.doNotSearchUpwards();
        BuildLayout layout = buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(noSearchParameter));
        SettingsState state = findSettingsAndLoadIfAppropriate(gradle, noSearchParameter, layout, classLoaderScope);

        // Set explicit build file, if required
        @SuppressWarnings("deprecation")
        File customBuildFile = DeprecationLogger.whileDisabled(noSearchParameter::getBuildFile);
        if (customBuildFile != null) {
            ProjectDescriptor rootProject = state.getSettings().getRootProject();
            rootProject.setBuildFileName(customBuildFile.getName());
        }
        return state;
    }

    private void setDefaultProject(ProjectSpec spec, SettingsInternal settings) {
        settings.setDefaultProject(spec.selectProject(settings.getSettingsScript().getDisplayName(), settings.getProjectRegistry()));
    }

    /**
     * Finds the settings.gradle for the given startParameter, and loads it if contains the project selected by the
     * startParameter, or if the startParameter explicitly specifies a settings script.  If the settings file is not
     * loaded (executed), then a null is returned.
     */
    private SettingsState findSettingsAndLoadIfAppropriate(
        GradleInternal gradle,
        StartParameter startParameter,
        SettingsLocation settingsLocation,
        ClassLoaderScope classLoaderScope
    ) {
        SettingsState state = settingsProcessor.process(gradle, settingsLocation, classLoaderScope, startParameter);
        validate(state.getSettings());
        return state;
    }

    private void validate(SettingsInternal settings) {
        settings.getProjectRegistry().getAllProjects().forEach(project -> {
            if (project.getPath().equals(BUILD_SRC_PROJECT_PATH)) {
                Path buildPath = settings.getGradle().getIdentityPath();
                String suffix = buildPath == Path.ROOT ? "" : " (in build " + buildPath + ")";
                throw new GradleException("'" + SettingsInternal.BUILD_SRC + "' cannot be used as a project name as it is a reserved name" + suffix);
            }
            if (!project.getProjectDir().exists() || !project.getProjectDir().isDirectory() || !project.getProjectDir().canWrite()) {
                emitProjectDirectoryMissingWarning(project.getPath(), project.getProjectDir().toString());
            }
        });
    }

    private static void emitProjectDirectoryMissingWarning(String projectPath, String projectDir) {
        String template = "Configuring project '%s' without an existing directory is deprecated. The configured projectDirectory '%s' does not exist, can't be written to or is not a directory.";
        DeprecationLogger.deprecateBehaviour(String.format(template, projectPath, projectDir))
            .withAdvice("Make sure the project directory exists and can be written.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecated_missing_project_directory")
            .nagUser();
    }
}
