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

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Collections;
import java.util.List;

/**
 * Represents a request to possibly create a configuration in a pre-defined {@link ConfigurationRole};
 * only if it doesn't already exist.
 *
 * It can be used to create warning and error messages that are specific to the context of the request and contain
 * actionable advice to avoid the problem.
 */
public interface RoleBasedConfigurationCreationRequest {
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
    ConfigurationRole getRole();

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

    /**
     * Issues a deprecation warning when a configuration already exists and Gradle needs to mutate its
     * usage to match the role in the request.
     *
     * @param conf the existing configuration
     */
    void warnAboutNeedToMutateUsage(DeprecatableConfiguration conf);

    /**
     * Throws an exception when Gradle fails to mutate the usage of a pre-existing configuration.
     *
     * @throws UnmodifiableUsageException always
     */
    void failOnInabilityToMutateUsage();

    /**
     * Ensures the usage of the requested configuration is consistent
     * with the role in the request.
     *
     * This method should only be called by the container when maybe-creating a configuration that already exists.
     *
     * This method will emit a detailed deprecation method with suggestions if the usage is inconsistent.  It will then attempt to mutate
     * the usage to match the expectation.  If the usage cannot be mutated, it will throw an exception.
     *
     * Does <strong>NOT</strong> check anything to do with deprecated usage.
     *
     * @param conf the existing configuration
     * @return the existing configuration, with its usage now matching the requested usage
     * @throws UnmodifiableUsageException if the usage doesn't match this request and cannot be mutated
     */
    Configuration verifyExistingConfigurationUsage(Configuration conf);

    /**
     * An exception thrown when Gradle cannot mutate the usage of a configuration that already
     * exists, but does not match the expected usage for the given role.
     */
    final class UnmodifiableUsageException extends GradleException implements ResolutionProvider {
        private final List<String> resolutions;

        public UnmodifiableUsageException(String configurationName, List<String> resolutions) {
            super(String.format("Gradle cannot mutate the usage of configuration '%s' because it is locked.", configurationName));
            this.resolutions = resolutions;
        }

        @Override
        public List<String> getResolutions() {
            return Collections.unmodifiableList(resolutions);
        }
    }
}
