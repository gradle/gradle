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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.deprecation.DocumentedFailure;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Static utility to verify a set of variants each have a unique identity in terms of attributes and capabilities.
 */
public class VariantIdentityUniquenessVerifier {

    /**
     * Build a report of all possible variant uniqueness failures for the given configurations.
     */
    public static VerificationReport buildReport(ConfigurationsProvider configurations) {
        ListMultimap<VariantIdentity, ConfigurationInternal> byIdentity =
            MultimapBuilder.linkedHashKeys().arrayListValues().build();

        configurations.visitConsumable(configuration -> {
            if (!mustHaveUniqueAttributes(configuration)) {
                return;
            }

            byIdentity.put(VariantIdentity.from(configuration), configuration);
        });

        return new VerificationReport(byIdentity);
    }

    /**
     * Consumable, non-resolvable, non-default configurations with attributes must have unique attributes.
     */
    private static boolean mustHaveUniqueAttributes(Configuration configuration) {
        return !configuration.isCanBeResolved() &&
            !Dependency.DEFAULT_CONFIGURATION.equals(configuration.getName()) &&
            !configuration.getAttributes().isEmpty();
    }

    /**
     * A report tracking all possible variant uniqueness failures for a component.
     */
    public static class VerificationReport {

        private final ListMultimap<VariantIdentity, ConfigurationInternal> byIdentity;

        private VerificationReport(ListMultimap<VariantIdentity, ConfigurationInternal> byIdentity) {
            this.byIdentity = byIdentity;
        }

        /**
         * Get a failure that only checks variant uniqueness for the given configuration.
         */
        @Nullable
        public GradleException failureFor(ConfigurationInternal configuration, boolean withTaskAdvice) {
            List<ConfigurationInternal> collisions =
                byIdentity.get(VariantIdentity.from(configuration)).stream()
                    .filter(it -> !it.getName().equals(configuration.getName()))
                    .collect(Collectors.toList());

            if (collisions.isEmpty()) {
                return null;
            }

            return buildFailure(configuration, withTaskAdvice, collisions);
        }

        /**
         * Throw an exception if any variants have conflicting identities.
         */
        public void assertNoConflicts() {
            for (VariantIdentity identity : byIdentity.keySet()) {
                List<ConfigurationInternal> collisions = byIdentity.get(identity);
                if (collisions.size() > 1) {

                    ConfigurationInternal configuration = collisions.get(0);
                    List<ConfigurationInternal> filtered =
                        byIdentity.get(identity).stream()
                            .filter(it -> !it.getName().equals(configuration.getName()))
                            .collect(Collectors.toList());

                    throw  buildFailure(configuration, true, filtered);
                }
            }
        }

        private static GradleException buildFailure(
            ConfigurationInternal configuration,
            boolean withTaskAdvice,
            List<ConfigurationInternal> collisions
        ) {
            DocumentedFailure.Builder builder = DocumentedFailure.builder();
            String advice = "Consider adding an additional attribute to one of the configurations to disambiguate them.";
            if (withTaskAdvice) {
                advice += "  Run the 'outgoingVariants' task for more details.";
            }

            String message = "Consumable configurations with identical capabilities within a project (other than the default configuration) " +
                "must have unique attributes, but " + configuration.getDisplayName() + " and " + collisions + " contain identical attribute sets.";

            return builder.withSummary(message)
                .withAdvice(advice)
                .withUserManual("upgrading_version_7", "unique_attribute_sets")
                .build();
        }
    }

    /**
     * The identity of a variant -- its attributes and capabilities.
     */
    private static class VariantIdentity {
        private final ImmutableAttributes attributes;
        private final ImmutableCapabilities capabilities;

        private VariantIdentity(ImmutableAttributes attributes, ImmutableCapabilities capabilities) {
            this.attributes = attributes;
            this.capabilities = capabilities;
        }

        public static VariantIdentity from(ConfigurationInternal configuration) {
            return new VariantIdentity(
                configuration.getAttributes().asImmutable(),
                allCapabilitiesIncludingDefault(configuration)
            );
        }

        private static ImmutableCapabilities allCapabilitiesIncludingDefault(ConfigurationInternal conf) {
            Collection<? extends Capability> declaredCapabilities = conf.getOutgoing().getCapabilities();
            if (!declaredCapabilities.isEmpty()) {
                return ImmutableCapabilities.of(declaredCapabilities);
            }

            // If no capabilities are declared, use the implicit capability.
            Project project = conf.getDomainObjectContext().getProject();
            if (project == null) {
                return ImmutableCapabilities.EMPTY;
            }
            return ImmutableCapabilities.of(new ProjectDerivedCapability(project));
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
            return Objects.equals(attributes, that.attributes) &&
                   Objects.equals(capabilities, that.capabilities);
        }

        @Override
        public int hashCode() {
            return attributes.hashCode() ^ capabilities.hashCode();
        }
    }

}
