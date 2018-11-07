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

package org.gradle.internal.scan.config;

import org.gradle.util.VersionNumber;

class BuildScanPluginCompatibility {

    public static final VersionNumber MIN_SUPPORTED_VERSION = VersionNumber.parse("2.0.2");
    private static final String MIN_SUPPORTED_VERSION_DISPLAY = "2.0.2";
    public static final String UNSUPPORTED_PLUGIN_VERSION_MESSAGE =
        "This version of Gradle requires version " + MIN_SUPPORTED_VERSION_DISPLAY + " of the build scan plugin or later.\n"
            + "Please see https://gradle.com/scans/help/gradle-incompatible-plugin-version for more information.";

    // Used just to test the mechanism
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-scan-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Build scan support disabled by secret toggle";

    String unsupportedReason(VersionNumber pluginVersion) {
        if (isEarlierThan(pluginVersion, MIN_SUPPORTED_VERSION)) {
            return UNSUPPORTED_PLUGIN_VERSION_MESSAGE;
        }

        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return UNSUPPORTED_TOGGLE_MESSAGE;
        }

        return null;
    }

    private static boolean isEarlierThan(VersionNumber pluginVersion, VersionNumber minSupportedVersion) {
        return pluginVersion.compareTo(minSupportedVersion) < 0;
    }

}
