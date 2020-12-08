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
import org.gradle.internal.composite.ConfigurableIncludedPluginBuild;
import org.gradle.internal.composite.DefaultConfigurableIncludedBuild;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

public class IncludedBuildSpec {

    public final File rootDir;
    private final Action<? super ConfigurableIncludedBuild> configurer;
    private final Class<? extends DefaultConfigurableIncludedBuild> configurableType;

    private IncludedBuildSpec(File rootDir, Action<? super ConfigurableIncludedBuild> configurer, Class<? extends DefaultConfigurableIncludedBuild> configurableType) {
        this.rootDir = rootDir;
        this.configurer = configurer;
        this.configurableType = configurableType;
    }

    public static IncludedBuildSpec includedPluginBuild(File rootDir, Action<? super ConfigurableIncludedBuild> configurer) {
        return new IncludedBuildSpec(rootDir, configurer, ConfigurableIncludedPluginBuild.class);
    }

    public static IncludedBuildSpec includedBuild(File rootDir, Action<? super ConfigurableIncludedBuild> configurer) {
        return new IncludedBuildSpec(rootDir, configurer, DefaultConfigurableIncludedBuild.class);
    }

    public DefaultConfigurableIncludedBuild configureSpec(Instantiator instantiator) {
        DefaultConfigurableIncludedBuild configurable = instantiator.newInstance(configurableType, rootDir);
        configurer.execute(configurable);
        return configurable;
    }
}
