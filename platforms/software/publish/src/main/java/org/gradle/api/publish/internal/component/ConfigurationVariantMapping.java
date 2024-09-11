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
package org.gradle.api.publish.internal.component;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class ConfigurationVariantMapping {
    private final ConfigurationInternal outgoingConfiguration;
    private Action<? super ConfigurationVariantDetails> action;
    private final ObjectFactory objectFactory;

    public ConfigurationVariantMapping(ConfigurationInternal outgoingConfiguration, Action<? super ConfigurationVariantDetails> action, ObjectFactory objectFactory) {
        this.outgoingConfiguration = outgoingConfiguration;
        this.action = action;
        this.objectFactory = objectFactory;
    }

    public void addAction(Action<? super ConfigurationVariantDetails> action) {
        this.action = Actions.composite(this.action, action);
    }

    public void collectVariants(Consumer<UsageContext> collector) {
        outgoingConfiguration.runDependencyActions();
        outgoingConfiguration.markAsObserved();
        String outgoingConfigurationName = outgoingConfiguration.getName();

        if (!outgoingConfiguration.isTransitive()) {
            DeprecationLogger.warnOfChangedBehaviour("Publication ignores 'transitive = false' at configuration level", "Consider using 'transitive = false' at the dependency level if you need this to be published.")
                .withUserManual("publishing_ivy", "configurations_marked_as_non_transitive")
                .nagUser();
        }

        Set<String> seen = new HashSet<>();

        // Visit implicit sub-variant
        ConfigurationVariant defaultConfigurationVariant = objectFactory.newInstance(DefaultConfigurationVariant.class, outgoingConfiguration);
        visitVariant(collector, seen, defaultConfigurationVariant, outgoingConfigurationName);

        // Visit explicit sub-variants
        NamedDomainObjectContainer<ConfigurationVariant> subvariants = outgoingConfiguration.getOutgoing().getVariants();
        for (ConfigurationVariant subvariant : subvariants) {
            String publishedVariantName = outgoingConfigurationName + StringUtils.capitalize(subvariant.getName());
            visitVariant(collector, seen, subvariant, publishedVariantName);
        }
    }

    private void visitVariant(
        Consumer<UsageContext> collector,
        Set<String> seen,
        ConfigurationVariant subvariant,
        String name
    ) {
        DefaultConfigurationVariantDetails details = objectFactory.newInstance(DefaultConfigurationVariantDetails.class, subvariant);
        action.execute(details);

        if (!details.shouldPublish()) {
            return;
        }

        if (!seen.add(name)) {
            throw new InvalidUserDataException("Cannot add feature variant '" + name + "' as a variant with the same name is already registered");
        }

        collector.accept(new FeatureConfigurationVariant(
            name,
            outgoingConfiguration,
            subvariant,
            details.getMavenScope(),
            details.isOptional(),
            details.dependencyMappingDetails
        ));
    }

    // Cannot be private due to reflective instantiation
    static class DefaultConfigurationVariant implements ConfigurationVariant {
        private final ConfigurationInternal outgoingConfiguration;

        @Inject
        public DefaultConfigurationVariant(ConfigurationInternal outgoingConfiguration) {
            this.outgoingConfiguration = outgoingConfiguration;
        }

        @Override
        public PublishArtifactSet getArtifacts() {
            return outgoingConfiguration.getArtifacts();
        }

        @Override
        public void artifact(Object notation) {
            throw new InvalidUserCodeException("Cannot add artifacts during filtering");
        }

        @Override
        public void artifact(Object notation, Action<? super ConfigurablePublishArtifact> configureAction) {
            throw new InvalidUserCodeException("Cannot add artifacts during filtering");
        }

        @Override
        public String getName() {
            return outgoingConfiguration.getName();
        }

        @Override
        public Optional<String> getDescription() {
            return Optional.ofNullable(outgoingConfiguration.getDescription());
        }

        @Override
        public ConfigurationVariant attributes(Action<? super AttributeContainer> action) {
            throw new InvalidUserCodeException("Cannot mutate outgoing configuration during filtering");
        }

        @Override
        public AttributeContainer getAttributes() {
            return outgoingConfiguration.getAttributes();
        }
    }

    // Cannot be private due to reflective instantiation
    static class DefaultConfigurationVariantDetails implements ConfigurationVariantDetailsInternal {
        private final ConfigurationVariant variant;
        private final ObjectFactory objectFactory;
        private boolean skip = false;
        private String mavenScope = "compile";
        private boolean optional = false;
        private DefaultDependencyMappingDetails dependencyMappingDetails;

        @Inject
        public DefaultConfigurationVariantDetails(ConfigurationVariant variant, ObjectFactory objectFactory) {
            this.variant = variant;
            this.objectFactory = objectFactory;
        }

        @Override
        public ConfigurationVariant getConfigurationVariant() {
            return variant;
        }

        @Override
        public void skip() {
            skip = true;
        }

        @Override
        public void mapToOptional() {
            this.optional = true;
        }

        @Override
        public void mapToMavenScope(String scope) {
            this.mavenScope = assertValidScope(scope);
        }

        @Override
        public void dependencyMapping(Action<? super DependencyMappingDetails> action) {
            if (dependencyMappingDetails == null) {
                dependencyMappingDetails = objectFactory.newInstance(DefaultDependencyMappingDetails.class);
            }
            action.execute(dependencyMappingDetails);
        }

        private static String assertValidScope(String scope) {
            scope = scope.toLowerCase(Locale.ROOT);
            if ("compile".equals(scope) || "runtime".equals(scope)) {
                return scope;
            }
            throw new InvalidUserCodeException("Invalid Maven scope '" + scope + "'. You must choose between 'compile' and 'runtime'");
        }

        public boolean shouldPublish() {
            return !skip;
        }

        public String getMavenScope() {
            return mavenScope;
        }

        public boolean isOptional() {
            return optional;
        }
    }

    public static abstract class DefaultDependencyMappingDetails implements ConfigurationVariantDetailsInternal.DependencyMappingDetails {

        private Configuration resolutionConfiguration;

        @Override
        public void fromResolutionOf(Configuration configuration) {
            this.resolutionConfiguration = configuration;
        }

        @Nullable
        public Configuration getResolutionConfiguration() {
            return resolutionConfiguration;
        }
    }
}
