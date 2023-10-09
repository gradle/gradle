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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.internal.deprecation.DeprecationLogger;

/**
 * Represents the context of a request to possibly create a configuration if it doesn't already exist.
 *
 * It can be used to create warning and error messages that are specific to the context of the request and contain
 * actionable advice to avoid the problem.
 */
public interface ConfigurationCreationRequest {
    /**
     * The default advice when a configuration is created with a reserved name.
     *
     * @param configurationName the name of the configuration being requested
     * @return the default advice for a deprecation warning related to this request
     */
    static String getDefaultReservedNameAdvice(String configurationName) {
        return String.format("Do not create a configuration with the name %s.", configurationName);
    }

    String getConfigurationName();

    /**
     * Issues a deprecation warning about the creation of a configuration with a reserved name.
     */
    default void warnAboutReservedName() {
        DeprecationLogger.deprecateBehaviour("The configuration " + getConfigurationName() + " was created explicitly. This configuration name is reserved for creation by Gradle.")
            .withAdvice(getDefaultReservedNameAdvice(getConfigurationName()))
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "configurations_allowed_usage")
            .nagUser();
    }
}
