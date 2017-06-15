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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.util.VersionNumber;

class BuildScanPluginCompatibilityEnforcer {

    private static final VersionNumber MAX_UNSUPPORTED_VERSION = VersionNumber.parse("1.7.3");
    private static final VersionNumber MIN_SUPPORTED_VERSION = VersionNumber.parse("1.7.4");

    private static final String HELP_LINK = "https://gradle.com/scans/help/gradle-incompatible-plugin-version";

    // Two version numbers are required to deal with development versions.
    // 1.1-TIMESTAMP is < 1.1.
    private final VersionNumber maxUnsupportedVersion;
    private final VersionNumber minSupportedVersion;

    public static BuildScanPluginCompatibilityEnforcer create() {
        return new BuildScanPluginCompatibilityEnforcer(MAX_UNSUPPORTED_VERSION, MIN_SUPPORTED_VERSION);
    }

    @VisibleForTesting
    BuildScanPluginCompatibilityEnforcer(VersionNumber maxUnsupportedVersion, VersionNumber minSupportedVersion) {
        this.maxUnsupportedVersion = maxUnsupportedVersion;
        this.minSupportedVersion = minSupportedVersion;
    }

    void assertSupported(String pluginVersion) {
        VersionNumber pluginVersionNumber = VersionNumber.parse(pluginVersion);
        if (pluginVersionNumber.equals(VersionNumber.UNKNOWN)) {
            // Likely some kind of development version of the scan plugin.
            return;
        }

        if (pluginVersionNumber.compareTo(maxUnsupportedVersion) <= 0) {
            throw unsupported();
        }
    }

    UnsupportedBuildScanPluginVersionException unsupported() {
        return new UnsupportedBuildScanPluginVersionException("This version of Gradle requires version " + minSupportedVersion + " of the build scan plugin or later.\nPlease see " + HELP_LINK + " for more information.");
    }

}
