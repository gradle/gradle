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

import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.initialization.CacheConfigurationsHandlingSettingsLoader;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.properties.GradlePropertiesController;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader;
import org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader;
import org.gradle.internal.composite.CompositeBuildSettingsLoader;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultSettingsPreparer implements SettingsPreparer {

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationProgressEventEmitter emitter;
    @Nullable
    private final PublicBuildPath fromBuild;
    private final BuildStateRegistry buildRegistry;
    private final ProjectStateRegistry projectRegistry;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradlePropertiesController gradlePropertiesController;
    private final BuildIncluder buildIncluder;
    private final InitScriptHandler initScriptHandler;
    private final CacheConfigurationsInternal cacheConfigurations;
    private final JvmToolchainsConfigurationValidator jvmToolchainsConfigurationValidator;
    private final SettingsLoader settingsLoader;

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
        this.buildRegistry = buildRegistry;
        this.projectRegistry = projectRegistry;
        this.buildLayoutFactory = buildLayoutFactory;
        this.gradlePropertiesController = gradlePropertiesController;
        this.buildIncluder = buildIncluder;
        this.initScriptHandler = initScriptHandler;
        this.cacheConfigurations = cacheConfigurations;
        this.jvmToolchainsConfigurationValidator = jvmToolchainsConfigurationValidator;

        this.settingsLoader = new DefaultSettingsLoader(
            settingsProcessor,
            buildLayoutFactory,
            builtInCommands,
            problems
        );
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
        SettingsLoader buildSettingsLoader = gradle.isRootBuild() ? forTopLevelBuild() : forNestedBuild();
        buildSettingsLoader.findAndLoadSettings(gradle);
    }

    public SettingsLoader forTopLevelBuild() {
        return new GradlePropertiesHandlingSettingsLoader(
            new DaemonJvmToolchainsValidatingSettingsLoader(
                new CacheConfigurationsHandlingSettingsLoader(
                    new InitScriptHandlingSettingsLoader(
                        new CompositeBuildSettingsLoader(
                            new ChildBuildRegisteringSettingsLoader(
                                new CommandLineIncludedBuildSettingsLoader(
                                    this::findAndLoadSettings
                                ),
                                buildIncluder
                            ),
                            buildRegistry
                        ),
                        initScriptHandler
                    ),
                    cacheConfigurations
                ),
                jvmToolchainsConfigurationValidator
            ),
            buildLayoutFactory,
            gradlePropertiesController
        );
    }

    public SettingsLoader forNestedBuild() {
        return new GradlePropertiesHandlingSettingsLoader(
            new InitScriptHandlingSettingsLoader(
                new ChildBuildRegisteringSettingsLoader(
                    this::findAndLoadSettings,
                    buildIncluder),
                initScriptHandler),
            buildLayoutFactory,
            gradlePropertiesController
        );
    }

    private SettingsState findAndLoadSettings(GradleInternal gradle) {
        SettingsState state = settingsLoader.findAndLoadSettings(gradle);
        gradle.attachSettings(state);
        projectRegistry.registerProjects(gradle.getOwner(), state.getSettings().getProjectRegistry());
        return state;
    }
}
