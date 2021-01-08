/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.java.usagecontext;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.internal.Actions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;

import java.util.Set;

public class ConfigurationVariantMapping {
    private final ConfigurationInternal outgoingConfiguration;
    private Action<? super ConfigurationVariantDetails> action;
    private final Instantiator instantiator;

    public ConfigurationVariantMapping(ConfigurationInternal outgoingConfiguration, Action<? super ConfigurationVariantDetails> action, Instantiator instantiator) {
        this.outgoingConfiguration = outgoingConfiguration;
        this.action = action;
        this.instantiator = instantiator;
    }

    private void assertNoDuplicateVariant(String name, Set<String> seen) {
        if (!seen.add(name)) {
            throw new InvalidUserDataException("Cannot add feature variant '" + name + "' as a variant with the same name is already registered");
        }
    }

    public void addAction(Action<? super ConfigurationVariantDetails> action) {
        this.action = Actions.composite(this.action, action);
    }

    public void collectUsageContexts(final ImmutableCollection.Builder<UsageContext> outgoing) {
        if (!outgoingConfiguration.isTransitive()) {
            DeprecationLogger.warnOfChangedBehaviour("Publication ignores 'transitive = false' at configuration level.", "Consider using 'transitive = false' at the dependency level if you need this to be published.")
                .withUserManual("publishing_ivy", "configurations_marked_as_non_transitive")
                .nagUser();
        }
        Set<String> seen = Sets.newHashSet();
        ConfigurationVariant defaultConfigurationVariant = instantiator.newInstance(DefaultConfigurationVariant.class, outgoingConfiguration);
        ConfigurationVariantDetailsInternal details = instantiator.newInstance(DefaultConfigurationVariantDetails.class, defaultConfigurationVariant);
        action.execute(details);
        String outgoingConfigurationName = outgoingConfiguration.getName();
        if (details.shouldPublish()) {
            registerUsageContext(outgoing, seen, defaultConfigurationVariant, outgoingConfigurationName, details.getMavenScope(), details.isOptional());
        }
        NamedDomainObjectContainer<ConfigurationVariant> extraVariants = outgoingConfiguration.getOutgoing().getVariants();
        for (ConfigurationVariant variant : extraVariants) {
            details = new DefaultConfigurationVariantDetails(variant);
            action.execute(details);
            if (details.shouldPublish()) {
                String name = outgoingConfigurationName + StringUtils.capitalize(variant.getName());
                registerUsageContext(outgoing, seen, variant, name, details.getMavenScope(), details.isOptional());
            }
        }
    }

    private void registerUsageContext(ImmutableCollection.Builder<UsageContext> outgoing, Set<String> seen, ConfigurationVariant variant, String name, String scope, boolean optional) {
        assertNoDuplicateVariant(name, seen);
        outgoing.add(new FeatureConfigurationUsageContext(name, outgoingConfiguration, variant, scope, optional));
    }

    static class DefaultConfigurationVariant implements ConfigurationVariant {
        private final ConfigurationInternal outgoingConfiguration;

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
        public ConfigurationVariant attributes(Action<? super AttributeContainer> action) {
            throw new InvalidUserCodeException("Cannot mutate outgoing configuration during filtering");
        }

        @Override
        public AttributeContainer getAttributes() {
            outgoingConfiguration.preventFromFurtherMutation();
            return outgoingConfiguration.getAttributes();
        }
    }

    static class DefaultConfigurationVariantDetails implements ConfigurationVariantDetailsInternal {
        private final ConfigurationVariant variant;
        private boolean skip = false;
        private String mavenScope = "compile";
        private boolean optional = false;

        public DefaultConfigurationVariantDetails(ConfigurationVariant variant) {
            this.variant = variant;
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

        private static String assertValidScope(String scope) {
            scope = scope.toLowerCase();
            if ("compile".equals(scope) || "runtime".equals(scope)) {
                return scope;
            }
            throw new InvalidUserCodeException("Invalid Maven scope '" + scope + "'. You must choose between 'compile' and 'runtime'");
        }

        @Override
        public boolean shouldPublish() {
            return !skip;
        }

        @Override
        public String getMavenScope() {
            return mavenScope;
        }

        @Override
        public boolean isOptional() {
            return optional;
        }
    }
}
