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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.deprecation.DocumentedFailure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ensures all configurations have a unique variant identity in terms of
 * their attributes and capabilities.
 */
public final class ConfigurationVariantIdentityUniquenessVerifier {

    private ConfigurationVariantIdentityUniquenessVerifier() {}

    public static Map<String, GradleException> verifyUniqueness(ConfigurationsProvider configurations, boolean withTaskAdvice) {

        SetMultimap<VariantIdentity, ConfigurationInternal> identities = MultimapBuilder.SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        configurations.visitAll(configuration -> {
            if (mustHaveUniqueAttributes(configuration)) {
                identities.put(new VariantIdentity(configuration), configuration);
            }
        });

        Map<String, GradleException> collisions = new LinkedHashMap<>();
        for (VariantIdentity id : identities.keySet()) {
            Set<ConfigurationInternal> confs = identities.get(id);
            if (confs.size() > 1) {
                // Collision found.
                confs.forEach(conf -> collisions.put(conf.getName(), formatCollisionFailure(conf, confs, withTaskAdvice)));
            }
        }

        return collisions;
    }

    private static GradleException formatCollisionFailure(ConfigurationInternal self, Set<ConfigurationInternal> collisions, boolean withTaskAdvice) {
        String advice = "Consider adding an additional attribute to one of the configurations to disambiguate them.";
        if (withTaskAdvice) {
            advice += "  Run the 'outgoingVariants' task for more details.";
        }

        List<String> other = Sets.filter(collisions, c -> !self.equals(c)).stream()
            .map(ConfigurationInternal::getDisplayName)
            .collect(Collectors.toList());

        return DocumentedFailure.builder().withSummary(
                "Consumable configurations with identical capabilities within a project (other than the default configuration) " +
                    "must have unique attributes, but " + self.getDisplayName() + " and " + other + " contain identical attribute sets."
            )
            .withUserManual("upgrading_version_7", "unique_attribute_sets")
            .withAdvice(advice)
            .build();
    }

    /**
     * Consumable, non-resolvable, non-default configurations with attributes must have unique attributes.
     */
    private static boolean mustHaveUniqueAttributes(Configuration configuration) {
        return configuration.isCanBeConsumed() &&
            !configuration.isCanBeResolved() &&
            !Dependency.DEFAULT_CONFIGURATION.equals(configuration.getName()) &&
            !configuration.getAttributes().isEmpty();
    }

    private static class VariantIdentity {
        private final Map<Attribute<?>, ?> attributes;
        private final Set<? extends Capability> capabilities;

        public VariantIdentity(ConfigurationInternal configuration) {
            this.attributes = configuration.getAttributes().asMap();
            this.capabilities = allCapabilitiesIncludingDefault(configuration);
        }

        private static Set<? extends Capability> allCapabilitiesIncludingDefault(ConfigurationInternal conf) {
            if (conf.getOutgoing().getCapabilities().isEmpty()) {
                Project project = conf.getDomainObjectContext().getProject();
                if (project == null) {
                    return Collections.emptySet();
                }
                return Collections.singleton(new ProjectDerivedCapability(project));
            } else {
                return ImmutableSet.copyOf(conf.getOutgoing().getCapabilities());
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            VariantIdentity that = (VariantIdentity) o;
            return Objects.equals(attributes, that.attributes) && Objects.equals(capabilities, that.capabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attributes, capabilities);
        }
    }
}
