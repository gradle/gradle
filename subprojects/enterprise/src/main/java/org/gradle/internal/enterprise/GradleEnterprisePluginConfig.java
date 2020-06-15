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

package org.gradle.internal.enterprise;

import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Information eagerly conveyed about the plugin from Gradle to the plugin.
 */
@UsedByScanPlugin
public interface GradleEnterprisePluginConfig {

    enum BuildScanRequest {
        NONE, // no explicit request
        REQUESTED, // --scan is present, but not --no-scan
        SUPPRESSED // --no-scan is present
    }

    BuildScanRequest getBuildScanRequest();

    boolean isTaskExecutingBuild();

}
