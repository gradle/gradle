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

package org.gradle.plugin.management.internal.autoapply;

import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

/**
 * Auto-applied build-scan plugin information.
 *
 * Required by the {@code kotlin-dsl} module.
 */
public final class AutoAppliedBuildScanPlugin {

    public static final PluginId ID = new DefaultPluginId("com.gradle.build-scan");
    public static final String GROUP = "com.gradle";
    public static final String NAME = "build-scan-plugin";
    public static final String VERSION = "2.1";

    /**
     * Adds the {@code build-scan} plugin spec to the given {@link PluginDependenciesSpec} and returns the
     * created {@link PluginDependencySpec}.
     *
     * @see PluginDependenciesSpec#id(String)
     */
    public static PluginDependencySpec addBuildScanPluginDependencySpecTo(PluginDependenciesSpec plugins) {
        return plugins.id(ID.getId()).version(VERSION);
    }
}
