/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

/**
 * A mutable specification of a dependency on a plugin.
 * <p>
 * Can be used to specify the version of the plugin to use.
 * </p>
 * <p>
 * See {@link PluginDependenciesSpec} for more information about declaring plugin dependencies.
 * </p>
 */
public interface PluginDependencySpec {

    /**
     * Specify the version of the plugin to depend on.
     *
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version "1.0"
     * }
     * </pre>
     * <p>
     * By default, dependencies have no (i.e. {@code null}) version.
     * </p>
     * Core plugins must not include a version number specification.
     * Community plugins must include a version number specification.
     *
     * @param version the version string ({@code null} for no specified version, which is the default)
     * @return this
     */
    PluginDependencySpec version(@Nullable String version);

    /**
     * Specify the version of the plugin to depend on.
     *
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version libs.versions.myplugin
     * }
     * </pre>
     *
     * @param version the version provider, for example as found in a version catalog
     * @return this
     *
     * @since 7.2
     */
    @Incubating
    default PluginDependencySpec version(Provider<String> version) {
        // providers used in plugins block are necessarily at configuration time
        return this.version(version.forUseAtConfigurationTime().get());
    }

    /**
     * Specifies whether the plugin should be applied to the current project. Otherwise it is only put
     * on the project's classpath.
     * <p>
     * This is useful when reusing classes from a plugin or to apply a plugin to sub-projects:
     *
     * <pre>
     * plugins {
     *     id "org.company.myplugin" version "1.0" apply false
     * }
     *
     * subprojects {
     *     if (someCondition) {
     *         apply plugin: "org.company.myplugin"
     *     }
     * }
     * </pre>
     *
     * @param apply whether to apply the plugin to the current project or not. Defaults to true
     * @return this
     */
    PluginDependencySpec apply(boolean apply);

}
