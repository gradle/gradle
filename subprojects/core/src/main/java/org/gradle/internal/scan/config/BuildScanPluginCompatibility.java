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

public class BuildScanPluginCompatibility {

    public static final String FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY = "3.0";
    public static final VersionNumber FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION = VersionNumber.parse(FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION_DISPLAY);

    public static final String OLD_SCAN_PLUGIN_VERSION_MESSAGE =
        "The build scan plugin is not compatible with this version of Gradle.\n"
            + "Please see https://gradle.com/help/gradle-6-build-scan-plugin for more information.";

    // Used just to test the mechanism
    public static final String UNSUPPORTED_TOGGLE = "org.gradle.internal.unsupported-scan-plugin";
    public static final String UNSUPPORTED_TOGGLE_MESSAGE = "Build scan support disabled by secret toggle";

    String unsupportedReason() {
        if (Boolean.getBoolean(UNSUPPORTED_TOGGLE)) {
            return UNSUPPORTED_TOGGLE_MESSAGE;
        }
        return null;
    }

}
