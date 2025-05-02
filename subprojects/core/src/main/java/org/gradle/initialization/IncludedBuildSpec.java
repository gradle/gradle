/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.ConfigurableIncludedPluginBuild;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.composite.DefaultConfigurableIncludedBuild;
import org.gradle.internal.composite.DefaultConfigurableIncludedPluginBuild;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.management.internal.PluginRequests;

import java.io.File;

public abstract class IncludedBuildSpec {

    public final File rootDir;

    protected IncludedBuildSpec(File rootDir) {
        this.rootDir = rootDir;
    }

    public abstract BuildDefinition toBuildDefinition(StartParameterInternal startParameter, PublicBuildPath publicBuildPath, Instantiator instantiator);

    public static IncludedBuildSpec includedPluginBuild(File rootDir, Action<? super ConfigurableIncludedPluginBuild> configurer) {
        return new IncludedPluginBuildSpec(rootDir, configurer);
    }

    public static IncludedBuildSpec includedBuild(File rootDir, Action<ConfigurableIncludedBuild> configurer) {
        return new IncludedLibraryBuildSpec(rootDir, configurer);
    }

    private static class IncludedLibraryBuildSpec extends IncludedBuildSpec {

        private final Action<? super ConfigurableIncludedBuild> configurer;

        private IncludedLibraryBuildSpec(File rootDir, Action<? super ConfigurableIncludedBuild> configurer) {
            super(rootDir);
            this.configurer = configurer;
        }

        @Override
        public BuildDefinition toBuildDefinition(StartParameterInternal startParameter, PublicBuildPath publicBuildPath, Instantiator instantiator) {
            DefaultConfigurableIncludedBuild configurable = instantiator.newInstance(DefaultConfigurableIncludedBuild.class, rootDir);
            configurer.execute(configurable);

            return BuildDefinition.fromStartParameterForBuild(
                startParameter,
                configurable.getName(),
                rootDir,
                PluginRequests.EMPTY,
                configurable.getDependencySubstitutionAction(),
                publicBuildPath,
                false
            );
        }
    }

    private static class IncludedPluginBuildSpec extends IncludedBuildSpec {

        private final Action<? super ConfigurableIncludedPluginBuild> configurer;

        private IncludedPluginBuildSpec(File rootDir, Action<? super ConfigurableIncludedPluginBuild> configurer) {
            super(rootDir);
            this.configurer = configurer;
        }

        @Override
        public BuildDefinition toBuildDefinition(StartParameterInternal startParameter, PublicBuildPath publicBuildPath, Instantiator instantiator) {
            DefaultConfigurableIncludedPluginBuild configurable = instantiator.newInstance(DefaultConfigurableIncludedPluginBuild.class, rootDir);
            configurer.execute(configurable);

            return BuildDefinition.fromStartParameterForBuild(
                startParameter,
                configurable.getName(),
                rootDir,
                PluginRequests.EMPTY,
                ImmutableActionSet.empty(),
                publicBuildPath,
                true
            );
        }
    }
}

