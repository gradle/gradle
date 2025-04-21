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
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationCreationRequest;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;

/**
 * The default implementation of {@link RoleBasedConfigurationCreationRequest}.
 */
public final class DefaultRoleBasedConfigurationCreationRequest implements RoleBasedConfigurationCreationRequest {
    private final String configurationName;
    private final ConfigurationRole role;
    private final InternalProblems problemsService;

    public DefaultRoleBasedConfigurationCreationRequest(String configurationName, ConfigurationRole role, InternalProblems problemsService) {
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

    @Override
    public RuntimeException failOnReservedName() {
        GradleException ex = new GradleException("The configuration " + getConfigurationName() + " was created explicitly. This configuration name is reserved for creation by Gradle.");
        ProblemId id = ProblemId.create("unexpected configuration usage", "Unexpected configuration usage", GradleCoreProblemGroup.configurationUsage());
        throw problemsService.getInternalReporter().throwing(ex, id, spec -> {
            spec.contextualLabel(ex.getMessage());
            spec.severity(Severity.ERROR);
        });
    }
}
