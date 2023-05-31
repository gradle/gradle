/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.util.internal.VersionNumber;

/**
 * Information about the earliest supported Gradle Enterprise plugin version.
 */
public class UnsupportedGradleEnterprisePluginUtil {

    // Gradle versions 9+ are not compatible Gradle Enterprise plugin < 3.13.1
    public static final String MINIMUM_SUPPORTED_PLUGIN_VERSION_DISPLAY = "3.13.1";
    public static final VersionNumber MINIMUM_SUPPORTED_PLUGIN_VERSION = VersionNumber.parse(MINIMUM_SUPPORTED_PLUGIN_VERSION_DISPLAY);

    public static boolean isUnsupportedPluginVersion(VersionNumber pluginBaseVersion) {
        return MINIMUM_SUPPORTED_PLUGIN_VERSION.compareTo(pluginBaseVersion) > 0;
    }

    public static String getUnsupportedPluginMessage(String pluginVersion) {
        return String.format(
            "Gradle Enterprise plugin %s has been disabled as it is incompatible with this version of Gradle. Upgrade to Gradle Enterprise plugin %s or newer to restore functionality.",
            pluginVersion,
            MINIMUM_SUPPORTED_PLUGIN_VERSION_DISPLAY
        );
    }

}
