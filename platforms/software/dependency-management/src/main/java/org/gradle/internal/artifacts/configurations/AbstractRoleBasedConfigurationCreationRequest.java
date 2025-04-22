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

package org.gradle.internal.artifacts.configurations;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationCreationRequest;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.deprecation.DeprecatableConfiguration;

/**
 * An {@code abstract} implementation of {@link RoleBasedConfigurationCreationRequest} that
 * provides basic common functionality.
 */
public abstract class AbstractRoleBasedConfigurationCreationRequest implements RoleBasedConfigurationCreationRequest {
    protected final String configurationName;
    protected final ConfigurationRole role;
    protected final InternalProblems problemsService;

    protected AbstractRoleBasedConfigurationCreationRequest(String configurationName, ConfigurationRole role, InternalProblems problemsService) {
        this.configurationName = configurationName;
        this.role = role;
        this.problemsService = problemsService;
    }

    @Override
    public String getConfigurationName() {
        return configurationName;
    }

    @Override
    public ConfigurationRole getRole() {
        return role;
    }

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
     * @return the existing configuration, with its usage now matching the requested usage
     * @throws UnmodifiableUsageException if the usage doesn't match this request and cannot be mutated
     */
    @Override
    public Configuration verifyExistingConfigurationUsage(Configuration conf) {
        warnAboutReservedName();
        ConfigurationInternal internalConf = (ConfigurationInternal) conf;

        if (!getRole().isUsageConsistentWithRole(conf)) {
            failOnNeedToMutateUsage(internalConf);
        }

        return conf;
    }

    @Override
    public void failOnNeedToMutateUsage(DeprecatableConfiguration conf) {
        String msgDiscovery = getUsageDiscoveryMessage(conf);
        String msgExpectation = getUsageExpectationMessage();

        GradleException ex = new GradleException(msgDiscovery + msgExpectation);
        ProblemId id = ProblemId.create("unexpected configuration usage", "Unexpected configuration usage", GradleCoreProblemGroup.configurationUsage());
        throw problemsService.getInternalReporter().throwing(ex, id, spec -> {
            spec.contextualLabel(ex.getMessage());
            spec.severity(Severity.ERROR);
        });
    }

    protected abstract String getUsageDiscoveryMessage(DeprecatableConfiguration conf);

    protected abstract String getUsageExpectationMessage();
}
