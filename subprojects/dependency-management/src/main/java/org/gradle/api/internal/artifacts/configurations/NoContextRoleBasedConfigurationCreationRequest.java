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

import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.Collections;

/**
 * An {@link AbstractRoleBasedConfigurationCreationRequest} that does not provide any additional contextual
 * information about the request, and emits generic error messages.
 */
public final class NoContextRoleBasedConfigurationCreationRequest extends AbstractRoleBasedConfigurationCreationRequest {
    public NoContextRoleBasedConfigurationCreationRequest(String configurationName, ConfigurationRole role) {
        super(configurationName, role);
    }

    private String getUsageDiscoveryMessage(DeprecatableConfiguration conf) {
        String currentUsageDesc = UsageDescriber.describeCurrentUsage(conf);
        return String.format("Configuration %s already exists with permitted usage(s):\n" +
            "%s\n", getConfigurationName(), currentUsageDesc);
    }

    private String getUsageExpectationMessage() {
        String expectedUsageDesc = UsageDescriber.describeRole(getRole());
        return String.format("Yet Gradle expected to create it with the usage(s):\n" +
            "%s\n" +
            "Gradle will mutate the usage of configuration %s to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names", expectedUsageDesc, getConfigurationName());
    }

    @Override
    public void warnAboutNeedToMutateUsage(DeprecatableConfiguration conf) {
        String msgDiscovery = getUsageDiscoveryMessage(conf);
        String msgExpectation = getUsageExpectationMessage();

        DeprecationLogger.deprecate(msgDiscovery + msgExpectation)
            .withAdvice(RoleBasedConfigurationCreationRequest.getDefaultReservedNameAdvice(configurationName))
            .willBecomeAnErrorInGradle9()
            .withUserManual("authoring_maintainable_build_scripts", "sec:dont_anticipate_configuration_creation")
            .nagUser();
    }

    @Override
    public void failOnInabilityToMutateUsage() {
        throw new UnmodifiableUsageException(getConfigurationName(), Collections.singletonList(RoleBasedConfigurationCreationRequest.getDefaultReservedNameAdvice(getConfigurationName())));
    }
}
