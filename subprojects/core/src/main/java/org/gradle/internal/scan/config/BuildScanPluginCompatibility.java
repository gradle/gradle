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

    public static final VersionNumber MIN_SUPPORTED_VERSION = VersionNumber.parse("1.8");
    public static final String UNSUPPORTED_PLUGIN_VERSION_MESSAGE =
        "This version of Gradle requires version " + MIN_SUPPORTED_VERSION + " of the build scan plugin or later.\n"
            + "Please see https://gradle.com/scans/help/gradle-incompatible-plugin-version for more information.";

    public static final VersionNumber MIN_VERSION_AWARE_OF_VCS_MAPPINGS = VersionNumber.parse("1.11");
    public static final String UNSUPPORTED_VCS_MAPPINGS_MESSAGE =
        "Build scans are not supported when using VCS mappings. It may be supported when using newer versions of the build scan plugin.";

    // Used just to test the mechanism
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-scan-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Build scan support disabled by secret toggle";

    String unsupportedReason(VersionNumber pluginVersion, BuildScanConfig.Attributes attributes) {
        if (pluginVersion.compareTo(MIN_SUPPORTED_VERSION) < 0) {
            return UNSUPPORTED_PLUGIN_VERSION_MESSAGE;
        }

        if (pluginVersion.compareTo(MIN_VERSION_AWARE_OF_VCS_MAPPINGS) < 0 && attributes.isRootProjectHasVcsMappings()) {
            return UNSUPPORTED_VCS_MAPPINGS_MESSAGE;
        }

        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return UNSUPPORTED_TOGGLE_MESSAGE;
        }

        return null;
    }

    UnsupportedBuildScanPluginVersionException unsupportedVersionException() {
        return new UnsupportedBuildScanPluginVersionException(UNSUPPORTED_PLUGIN_VERSION_MESSAGE);
    }
}
