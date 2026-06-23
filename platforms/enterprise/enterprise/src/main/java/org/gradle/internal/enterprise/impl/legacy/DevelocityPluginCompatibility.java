/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.enterprise.impl.legacy;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.util.internal.VersionNumber;

/**
 * Information about the earliest supported Develocity plugin version.
 * <p>
 * Develocity plugin has followed and taken over the versioning of the Gradle Enterprise plugin.
 * <ul>
 * <li> Develocity plugin is published from 3.17 and later
 * <li> Gradle Enterprise plugin (legacy) has been published for all supported versions until 4.0 (not including).
 * </ul>
 */
public class DevelocityPluginCompatibility {

    // Gradle versions 9+ are not compatible with Gradle Enterprise plugin < 3.13.1
    @VisibleForTesting
    public static final String MINIMUM_SUPPORTED_PLUGIN_VERSION = "3.13.1";
    @VisibleForTesting
    public static final VersionNumber MINIMUM_SUPPORTED_PLUGIN_VERSION_NUMBER = VersionNumber.parse(MINIMUM_SUPPORTED_PLUGIN_VERSION);

    /**
     * Gradle 9.6.0 changes IP behavior by removing the properties lookup in parent projects.
     * This has the same effects on the plugin as described in {@link #FIRST_PLUGIN_VERSION_WITHOUT_PARENT_PROPERTY_LOOKUP}.
     * That's why we require 4.0+ when running with IP already starting with 9.6.0.
     */
    private static final String ISOLATED_PROJECTS_SUPPORTED_PLUGIN_VERSION = "4.0";
    private static final VersionNumber ISOLATED_PROJECTS_SUPPORTED_PLUGIN_VERSION_NUMBER = VersionNumber.parse(ISOLATED_PROJECTS_SUPPORTED_PLUGIN_VERSION);

    /**
     * Develocity plugin 4.0 is the first version that registers its project extension on every project,
     * so configuring {@code develocity { ... }} from a subproject no longer relies on Gradle's
     * implicit parent-project property lookup. That implicit lookup is being removed in Gradle 10,
     * so versions below 4.0 are deprecated to give users time to upgrade ahead of the change.
     */
    @VisibleForTesting
    public static final String FIRST_PLUGIN_VERSION_WITHOUT_PARENT_PROPERTY_LOOKUP = "4.0";
    @VisibleForTesting
    public static final VersionNumber FIRST_PLUGIN_VERSION_WITHOUT_PARENT_PROPERTY_LOOKUP_NUMBER = VersionNumber.parse(FIRST_PLUGIN_VERSION_WITHOUT_PARENT_PROPERTY_LOOKUP);

    public static boolean isUnsupportedPluginVersion(VersionNumber pluginBaseVersion) {
        return MINIMUM_SUPPORTED_PLUGIN_VERSION_NUMBER.compareTo(pluginBaseVersion) > 0;
    }

    public static String getUnsupportedPluginMessage(String pluginVersion) {
        return String.format(
            "Gradle Enterprise plugin %s has been disabled as it is incompatible with this version of Gradle. Upgrade to Gradle Enterprise plugin %s or newer to restore functionality.",
            pluginVersion,
            MINIMUM_SUPPORTED_PLUGIN_VERSION
        );
    }

    public static boolean isUnsupportedWithIsolatedProjects(VersionNumber pluginBaseVersion) {
        return ISOLATED_PROJECTS_SUPPORTED_PLUGIN_VERSION_NUMBER.compareTo(pluginBaseVersion) > 0;
    }

    public static String getUnsupportedWithIsolatedProjectsMessage(String pluginVersion) {
        return String.format(
            "Gradle Enterprise plugin %s has been disabled as it is incompatible with Isolated Projects. Upgrade to Gradle Enterprise plugin %s or newer to restore functionality.",
            pluginVersion,
            ISOLATED_PROJECTS_SUPPORTED_PLUGIN_VERSION
        );
    }

    public static boolean isAffectedByParentPropertyLookup(VersionNumber pluginBaseVersion) {
        return FIRST_PLUGIN_VERSION_WITHOUT_PARENT_PROPERTY_LOOKUP_NUMBER.compareTo(pluginBaseVersion) > 0;
    }
}
