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

import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.Collections;
import java.util.List;

/**
 * A request to create a configuration if it doesn't already exist (and to
 * ensure it is in the specified role if it does).
 */
public interface ConfigurationCreationRequest {
    static String getDefaultAdvice(String configurationName) {
        return String.format("Do not create a configuration with the name %s.", configurationName);
    }
    static DocumentationSpec getDefaultAdditionalDocumentation() {
        return new DocumentationSpec("authoring_maintainable_build_scripts", "sec:dont_anticipate_configuration_creation");
    }

    static ConfigurationCreationRequest noContext(String configurationName, ConfigurationRole role) {
        return new AbstractConfigurationCreationRequest(configurationName, role) { /* uses all defaults */ };
    }

    String getConfigurationName();
    ConfigurationRole getRole();

    default String getAdvice() {
        return getDefaultAdvice(getConfigurationName());
    }

    default DocumentationSpec getAdditionalDocumentation() {
        return getDefaultAdditionalDocumentation();
    }

    default String getDiscoveryMessage(DeprecatableConfiguration conf) {
        String currentUsageDesc = UsageDescriber.describeCurrentUsage(conf);
        return String.format("Configuration %s already exists with permitted usage(s):\n" +
            "%s\n", getConfigurationName(), currentUsageDesc);
    }

    default String getExpectationMessage(@SuppressWarnings("unused") DeprecatableConfiguration conf) {
        String expectedUsageDesc = UsageDescriber.describeRole(getRole());
        return String.format("Yet Gradle expected to create it with the usage(s):\n" +
            "%s\n" +
            "Gradle will mutate the usage of configuration %s to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names", expectedUsageDesc, getConfigurationName());
    }

    default void warnAboutReservedName() {
        DeprecationLogger.deprecateBehaviour("The configuration " + getConfigurationName() + " was created explicitly. This configuration name is reserved for creation by Gradle.")
            .withAdvice(String.format("Do not create a configuration with the name %s.", getConfigurationName()))
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "configurations_allowed_usage")
            .nagUser();
    }

    default void warnAboutNeedToMutateRole(DeprecatableConfiguration conf) {
        String msgDiscovery = getDiscoveryMessage(conf);
        String msgExpectation = getExpectationMessage(conf);

        DeprecationLogger.deprecate(msgDiscovery + msgExpectation)
            .withAdvice(getAdvice())
            .willBecomeAnErrorInGradle9()
            .withUserManual(getAdditionalDocumentation().getDocumentationId(), getAdditionalDocumentation().getSection())
            .nagUser();
    }

    default void errorOnInabilityToMutateRole() {
        List<String> resolutions = Lists.newArrayList(getDefaultAdvice(getConfigurationName()));
        resolutions.add(getAdvice());
        throw new UnmodifiableConfigurationException(getConfigurationName(), resolutions);
    }

    class DocumentationSpec {
        private final String documentationId;
        private final String section;

        public DocumentationSpec(String documentationId, String section) {
            this.documentationId = documentationId;
            this.section = section;
        }

        public String getDocumentationId() {
            return documentationId;
        }

        public String getSection() {
            return section;
        }
    }

    /**
     * An exception to be thrown when Gradle cannot mutate the usage of a configuration.
     */
    class UnmodifiableConfigurationException extends GradleException implements ResolutionProvider {
        private final List<String> resolutions;

        public UnmodifiableConfigurationException(String configurationName, List<String> resolutions) {
            super(String.format("Gradle cannot mutate the usage of configuration '%s' because it is locked.", configurationName));
            this.resolutions = resolutions;
        }

        @Override
        public List<String> getResolutions() {
            return Collections.unmodifiableList(resolutions);
        }
    }

    abstract class AbstractConfigurationCreationRequest implements ConfigurationCreationRequest {
        protected final String configurationName;
        protected final ConfigurationRole role;

        protected AbstractConfigurationCreationRequest(String configurationName, ConfigurationRole role) {
            this.configurationName = configurationName;
            this.role = role;
        }

        @Override
        public String getConfigurationName() {
            return configurationName;
        }

        @Override
        public ConfigurationRole getRole() {
            return role;
        }
    }
}
