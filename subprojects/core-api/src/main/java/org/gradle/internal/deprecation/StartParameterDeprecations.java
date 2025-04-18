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

package org.gradle.internal.deprecation;

import org.gradle.StartParameter;

public class StartParameterDeprecations {

    public static void nagOnIsConfigurationCacheRequested() {
        DeprecationLogger.deprecateProperty(StartParameter.class, "isConfigurationCacheRequested")
            .withAdvice("Please use 'configurationCache.requested' property on 'BuildFeatures' service instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(8, "deprecated_startparameter_is_configuration_cache_requested")
            .nagUser();
    }
}
