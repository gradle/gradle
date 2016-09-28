/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.IncludedBuildFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;

import java.io.File;
import java.util.Set;

public class DefaultIncludedBuildFactory implements IncludedBuildFactory, Stoppable {
    private final Instantiator instantiator;
    private final StartParameter startParameter;
    private final GradleLauncherFactory gradleLauncherFactory;
    private final ServiceRegistry sharedServices;
    private final Set<GradleLauncher> launchers = Sets.newHashSet();

    public DefaultIncludedBuildFactory(Instantiator instantiator, StartParameter startParameter,
                                       GradleLauncherFactory gradleLauncherFactory, ServiceRegistry sharedServices) {
        this.instantiator = instantiator;
        this.startParameter = startParameter;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.sharedServices = sharedServices;
    }

    @Override
    public ConfigurableIncludedBuild createBuild(File buildDirectory, BuildRequestContext requestContext) {
        validateBuildDirectory(buildDirectory);
        Factory<GradleLauncher> factory = new ContextualGradleLauncherFactory(buildDirectory, gradleLauncherFactory, startParameter, requestContext, sharedServices);
        Factory<GradleLauncher> nestedFactory = requestContext == null ? null : new ContextualGradleLauncherFactory(buildDirectory, gradleLauncherFactory, startParameter, null, sharedServices);
        DefaultIncludedBuild includedBuild = instantiator.newInstance(DefaultIncludedBuild.class, buildDirectory, factory, nestedFactory);

        validateIncludedBuild(includedBuild, buildDirectory);
        includedBuild.initialize();
        return includedBuild;
    }

    private void validateBuildDirectory(File dir) {
        if (!dir.exists()) {
            throw new InvalidUserDataException(String.format("Included build '%s' does not exist.", dir));
        }
        if (!dir.isDirectory()) {
            throw new InvalidUserDataException(String.format("Included build '%s' is not a directory.", dir));
        }
    }

    private void validateIncludedBuild(DefaultIncludedBuild includedBuild, File buildDirectory) {
        SettingsInternal settings = includedBuild.initialize();
        if (!new File(settings.getSettingsDir(), "settings.gradle").exists()) {
            throw new InvalidUserDataException(String.format("Included build '%s' must have a 'settings.gradle' file.", buildDirectory.getName()));
        }
        if (!settings.getIncludedBuilds().isEmpty()) {
            throw new InvalidUserDataException(String.format("Included build '%s' cannot have included builds.", includedBuild.getName()));
        }
    }

    @Override
    public ConfigurableIncludedBuild createBuild(File buildDirectory) {
        return createBuild(buildDirectory, null);
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(launchers).stop();
    }

    private class ContextualGradleLauncherFactory implements Factory<GradleLauncher> {
        private final File buildDirectory;
        private final GradleLauncherFactory gradleLauncherFactory;
        private final StartParameter buildStartParam;
        private final BuildRequestContext requestContext;
        private final ServiceRegistry sharedServices;

        public ContextualGradleLauncherFactory(File buildDirectory, GradleLauncherFactory gradleLauncherFactory, StartParameter buildStartParam, BuildRequestContext requestContext, ServiceRegistry sharedServices) {
            this.buildDirectory = buildDirectory;
            this.gradleLauncherFactory = gradleLauncherFactory;
            this.buildStartParam = buildStartParam;
            this.requestContext = requestContext;
            this.sharedServices = sharedServices;
        }

        @Override
        public GradleLauncher create() {
            StartParameter participantStartParam = createStartParameter(buildDirectory);

            GradleLauncher gradleLauncher;
            if (requestContext == null) {
                gradleLauncher = gradleLauncherFactory.nestedInstance(participantStartParam, sharedServices);
            } else {
                gradleLauncher = gradleLauncherFactory.newInstance(participantStartParam, requestContext, sharedServices);
                gradleLauncher.addStandardOutputListener(requestContext.getOutputListener());
                gradleLauncher.addStandardErrorListener(requestContext.getErrorListener());
            }

            launchers.add(gradleLauncher);
            return gradleLauncher;
        }

        private StartParameter createStartParameter(File buildDirectory) {
            StartParameter includedBuildStartParam = buildStartParam.newBuild();
            includedBuildStartParam.setProjectDir(buildDirectory);
            includedBuildStartParam.setSearchUpwards(false);
            // TODO:DAZ Consider if we still really need this
            includedBuildStartParam.setConfigureOnDemand(false);
            return includedBuildStartParam;
        }
    }
}
