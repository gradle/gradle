/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.initialization.dsl.DependenciesModelBuilder;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.plugin.use.PluginDependenciesSpec;

/**
 * Allows configuring a Gradle platform.
 *
 * @since 6.8
 */
@Incubating
@HasInternalProtocol
public interface GradlePlatformExtension {
    /**
     * Configures the dependency model of this platform.
     * @param spec the spec used to configure the dependencies
     */
    void dependenciesModel(Action<? super DependenciesModelBuilder> spec);

    /**
     * Configures the plugins model of this platform.
     * Currently it's only possible to configure the default versions
     * of plugins.
     *
     * @param spec the spec used to configure the plugins
     */
    void plugins(Action<? super PluginDependenciesSpec> spec);

    /**
     * Configures an explicit alias for a dependency in case of name clash
     */
    void configureExplicitAlias(String alias, String group, String name);
}
