/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.properties.GradlePropertiesController;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.initialization.buildsrc.BuildSrcDetector;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.scripts.ScriptResolutionResult;
import org.gradle.internal.scripts.ScriptResolutionResultReporter;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultSettingsPreparer implements SettingsPreparer {

    private static final String BUILD_SRC_PROJECT_PATH = ":" + BuildLogicFiles.BUILD_SOURCE_DIRECTORY;

    private static final Logger logger = Logging.getLogger(DefaultSettingsPreparer.class);

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationProgressEventEmitter emitter;
    @Nullable
    private final PublicBuildPath fromBuild;
    private final SettingsProcessor settingsProcessor;
    private final BuildStateRegistry buildRegistry;
    private final ProjectStateRegistry projectRegistry;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradlePropertiesController gradlePropertiesController;
    private final BuildIncluder buildIncluder;
    private final InitScriptHandler initScriptHandler;
    private final List<BuiltInCommand> builtInCommands;
    private final CacheConfigurationsInternal cacheConfigurations;
    private final InternalProblems problems;
    private final JvmToolchainsConfigurationValidator jvmToolchainsConfigurationValidator;

    public DefaultSettingsPreparer(
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter emitter,
        BuildDefinition buildDefinition,
        SettingsProcessor settingsProcessor,
        BuildStateRegistry buildRegistry,
        ProjectStateRegistry projectRegistry,
        BuildLayoutFactory buildLayoutFactory,
        GradlePropertiesController gradlePropertiesController,
        BuildIncluder buildIncluder,
        InitScriptHandler initScriptHandler,
        List<BuiltInCommand> builtInCommands,
        CacheConfigurationsInternal cacheConfigurations,
        InternalProblems problems,
        JvmToolchainsConfigurationValidator jvmToolchainsConfigurationValidator
    ) {
        this.buildOperationRunner = buildOperationRunner;
        this.emitter = emitter;
        this.fromBuild = buildDefinition.getFromBuild();
        this.settingsProcessor = settingsProcessor;
        this.buildRegistry = buildRegistry;
        this.projectRegistry = projectRegistry;
        this.buildLayoutFactory = buildLayoutFactory;
        this.gradlePropertiesController = gradlePropertiesController;
        this.buildIncluder = buildIncluder;
        this.initScriptHandler = initScriptHandler;
        this.builtInCommands = builtInCommands;
        this.cacheConfigurations = cacheConfigurations;
        this.problems = problems;
        this.jvmToolchainsConfigurationValidator = jvmToolchainsConfigurationValidator;
    }

    @Override
    public void prepareSettings(GradleInternal gradle) {
        //noinspection Convert2Lambda
        emitter.emitNowForCurrent(new BuildIdentifiedProgressDetails() {
            @Override
            public String getBuildPath() {
                return gradle.getIdentityPath().toString();
            }
        });

        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                loadBuild(gradle);
                context.setResult(new LoadBuildBuildOperationType.Result() {});
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(gradle.contextualize("Load build"))
                    .details(new LoadBuildBuildOperationType.Details() {
                        @Override
                        public String getBuildPath() {
                            return gradle.getIdentityPath().toString();
                        }

                        @Override
                        public String getIncludedBy() {
                            return fromBuild == null ? null : fromBuild.getBuildPath().toString();
                        }
                    });
            }
        });
    }

    private void loadBuild(GradleInternal gradle) {
        loadGradlePropertiesForBuild(gradle);

        if (gradle.isRootBuild()) {
            jvmToolchainsConfigurationValidator.validateAllPropertiesConfigurationsForDaemonJvmToolchains();

            cacheConfigurations.setCleanupHasBeenConfigured(false);
        }

        initScriptHandler.executeScripts(gradle);

        SettingsState state = findAndLoadSettings(gradle);
        gradle.attachSettings(state);

        SettingsInternal settings = state.getSettings();
        projectRegistry.registerProjects(gradle.getOwner(), settings.getProjectRegistry());

        if (gradle.isRootBuild()) {
            // Add all included builds from the command-line
            for (File includedBuildRootDir : gradle.getStartParameter().getIncludedBuilds()) {
                settings.includeBuild(includedBuildRootDir);
            }
        }

        // Add included builds defined in settings
        gradle.setIncludedBuilds(loadIncludedBuildsRecursively(settings));

        if (gradle.isRootBuild()) {
            // Lock-in explicitly included builds
            buildRegistry.finalizeIncludedBuilds();

            cacheConfigurations.setCleanupHasBeenConfigured(true);
        }
    }

    private void loadGradlePropertiesForBuild(GradleInternal gradle) {
        SettingsLocation settingsLocation = buildLayoutFactory.getLayoutFor(gradle.getStartParameter().toBuildLayoutConfiguration());
        BuildIdentifier buildId = gradle.getOwner().getBuildIdentifier();
        gradlePropertiesController.loadGradleProperties(buildId, settingsLocation.getSettingsDir(), true);
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private Set<IncludedBuildInternal> loadIncludedBuildsRecursively(SettingsInternal settings) {
        List<IncludedBuildSpec> includedBuilds = settings.getIncludedBuilds();
        if (includedBuilds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<IncludedBuildInternal> children = new LinkedHashSet<>(includedBuilds.size());
        for (IncludedBuildSpec includedBuildSpec : includedBuilds) {
            CompositeBuildParticipantBuildState includedBuild = buildIncluder.includeBuild(includedBuildSpec);
            children.add(includedBuild.getModel());
        }
        return children;
    }

    private SettingsState findAndLoadSettings(GradleInternal gradle) {
        StartParameterInternal startParameter = gradle.getStartParameter();
        BuildLayout buildLayout = buildLayoutFactory.getLayoutFor(startParameter.toBuildLayoutConfiguration());
        if (buildLayout.getSettingsFileResolution() != null) {
            ScriptResolutionResultReporter reporter = new ScriptResolutionResultReporter(problems.getReporter());
            reporter.reportResolutionProblemsOf(buildLayout.getSettingsFileResolution());
        }

        SettingsState state;
        ProjectSpec spec;
        if (shouldSkipLoadingBuildDefinition(startParameter)) {
            logger.debug("Skipping loading of build definition for build: '{}'", gradle.getIdentityPath());
            state = createEmptySettings(gradle, startParameter, gradle.getClassLoaderScope());
            spec = ProjectSpecs.forStartParameter(startParameter, state.getSettings());
        } else {
            logger.debug("Loading build definition for build: '{}'", gradle.getIdentityPath());
            state = findSettingsAndLoadIfAppropriate(gradle, startParameter, buildLayout, gradle.getClassLoaderScope());
            SettingsInternal settings = state.getSettings();
            spec = ProjectSpecs.forStartParameter(startParameter, settings);
            if (useEmptySettings(spec, settings, startParameter)) {
                // Discard the loaded settings and replace with an empty one
                logger.debug("Discarding loaded settings and replacing with empty settings for build: '{}'", gradle.getIdentityPath());
                state.close();
                state = createEmptySettings(gradle, startParameter, settings.getClassLoaderScope());
            }
        }

        SettingsInternal settings = state.getSettings();
        settings.setDefaultProject(spec.selectProject(settings.getSettingsScript().getDisplayName(), settings.getProjectRegistry()));

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

    private SettingsState createEmptySettings(GradleInternal gradle, StartParameter startParameter, ClassLoaderScope classLoaderScope) {
        logger.debug("Creating empty settings for build: '{}'", gradle.getIdentityPath());
        StartParameterInternal noSearchParameter = (StartParameterInternal) startParameter.newInstance();
        noSearchParameter.useEmptySettings();
        noSearchParameter.doNotSearchUpwards();
        BuildLayout layout = buildLayoutFactory.getLayoutFor(noSearchParameter.toBuildLayoutConfiguration());
        return findSettingsAndLoadIfAppropriate(gradle, noSearchParameter, layout, classLoaderScope);
    }

    /**
     * Finds the settings.gradle for the given startParameter, and loads it if contains the project selected by the
     * startParameter, or if the startParameter explicitly specifies a settings script.  If the settings file is not
     * loaded (executed), then a null is returned.
     */
    private SettingsState findSettingsAndLoadIfAppropriate(
        GradleInternal gradle,
        StartParameter startParameter,
        BuildLayout buildLayout,
        ClassLoaderScope classLoaderScope
    ) {
        ScriptResolutionResult resolutionResult = buildLayout.getSettingsFileResolution();
        if (resolutionResult != null) {
            new ScriptResolutionResultReporter(problems.getReporter()).reportResolutionProblemsOf(resolutionResult);
        }

        SettingsState state = settingsProcessor.process(gradle, buildLayout, classLoaderScope, startParameter);
        validate(state.getSettings());
        return state;
    }

    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private void validate(SettingsInternal settings) {
        settings.getProjectRegistry().getAllProjects().forEach(project -> {
            if (project.getPath().equals(BUILD_SRC_PROJECT_PATH)) {
                Path buildPath = settings.getGradle().getIdentityPath();
                String suffix = buildPath == Path.ROOT ? "" : " (in build " + buildPath + ")";
                throw new GradleException("'" + SettingsInternal.BUILD_SRC + "' cannot be used as a project name as it is a reserved name" + suffix);
            }
            if (!project.getProjectDir().exists() || !project.getProjectDir().isDirectory() || !project.getProjectDir().canWrite()) {
                failOnMissingProjectDirectory(project.getPath(), project.getProjectDir().toString());
            }
        });
    }

    private void failOnMissingProjectDirectory(String projectPath, String projectDir) {
        throw problems.getInternalReporter().throwing(
            new GradleException(
                String.format(
                    "Configuring project '%s' without an existing directory is not allowed. The configured projectDirectory '%s' does not exist, can't be written to or is not a directory.",
                    projectPath,
                    projectDir
                )
            ),
            ProblemId.create("configuring-project-with-invalid-directory", "Configuring project with invalid directory", GradleCoreProblemGroup.configurationUsage()),
            spec ->
                spec.solution("Make sure the project directory exists and is writable.")
                    .documentedAt(Documentation.userManual("multi_project_builds", "include_existing_projects_only").getUrl())
        );
    }
}
