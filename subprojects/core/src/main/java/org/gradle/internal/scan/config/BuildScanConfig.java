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

import org.gradle.StartParameter;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Represents the aspects of build scan configuration that Gradle contributes.
 * Does not include configuration aspects that the scan plugin manages (e.g. server address).
 * Currently, this is effectively the --scan and --no-scan invocation options.
 *
 * @since 4.0
 */
@UsedByScanPlugin
public interface BuildScanConfig {

    /**
     * Indicates whether a scan was <b>explicitly</b> requested.
     *
     * This effectively maps to {@link StartParameter#isBuildScan()}.
     */
    boolean isEnabled();

    /**
     * Indicates whether a scan was <b>explicitly not</b> requested.
     *
     * This effectively maps to {@link StartParameter#isNoBuildScan()}.
     */
    boolean isDisabled();

    /**
     * Indicates whether the build scan plugin should not apply itself because its known to be incompatible.
     *
     * @since 4.4
     */
    String getUnsupportedMessage();

    /**
     * Attributes about the build environment that the build scan plugin needs to know about.
     *
     * This is effectively an insulation layer between the plugin and internal API.
     *
     * @return the attributes
     * @since 4.4
     */
    Attributes getAttributes();

    interface Attributes {

        /**
         * No longer actually used, but needed for binary compatibility.
         */
        boolean isRootProjectHasVcsMappings();

        /**
         * Whether the currently executing build is intended to execute tasks.
         *
         * @since 5.0
         */
        boolean isTaskExecutingBuild();
    }


}
