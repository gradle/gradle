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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class DefaultAdhocSoftwareComponent implements AdhocComponentWithVariants, SoftwareComponentInternal {

    private final String componentName;
    private final ObjectFactory objectFactory;

    // Mutable state
    private final List<ConfigurationVariantAction> actions;
    private @Nullable ImmutableSet<UsageContext> cachedVariants;

    @Inject
    public DefaultAdhocSoftwareComponent(String componentName, ObjectFactory objectFactory) {
        this.componentName = componentName;
        this.objectFactory = objectFactory;
        this.actions = new ArrayList<>();
    }

    @Override
    public String getName() {
        return componentName;
    }

    @Override
    public void addVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> spec) {
        checkNotObserved();
        actions.add(new ConfigurationVariantAction(() -> outgoingConfiguration, spec, false));
    }

    @Override
    public void addVariantsFromConfiguration(Provider<ConsumableConfiguration> outgoingConfiguration, Action<? super ConfigurationVariantDetails> action) {
        checkNotObserved();
        actions.add(new ConfigurationVariantAction(outgoingConfiguration::get, action, false));
    }

    @Override
    public void withVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> action) {
        checkNotObserved();
        actions.add(new ConfigurationVariantAction(() -> outgoingConfiguration, action, true));
    }

    @Override
    public void withVariantsFromConfiguration(Provider<ConsumableConfiguration> outgoingConfiguration, Action<? super ConfigurationVariantDetails> action) {
        checkNotObserved();
        actions.add(new ConfigurationVariantAction(outgoingConfiguration::get, action, true));
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        if (cachedVariants == null) {
            cachedVariants = computeVariants();
        }
        return cachedVariants;
    }

    private ImmutableSet<UsageContext> computeVariants() {
        Map<Configuration, ConfigurationVariantMapping> variants = new LinkedHashMap<>(4);
        for (ConfigurationVariantAction action : actions) {
            Configuration configuration = action.getConfiguration();
            if (!action.isMutate()) {
                variants.put(configuration, new ConfigurationVariantMapping((ConfigurationInternal) configuration, action.getSpec(), objectFactory));
            } else {
                if (!variants.containsKey(configuration)) {
                    throw new InvalidUserDataException(
                        "Variant for configuration '" + configuration.getName() + "' does not exist in component '" + componentName + "'. " +
                            "For a given configuration, 'addVariantsFromConfiguration' must be called before 'withVariantsFromConfiguration'."
                    );
                }
                variants.get(configuration).addAction(action.getSpec());
            }
        }

        ImmutableSet.Builder<UsageContext> builder = new ImmutableSet.Builder<>();
        for (ConfigurationVariantMapping variant : variants.values()) {
            variant.collectVariants(builder::add);
        }
        return builder.build();
    }

    /**
     * Ensure this component cannot be modified after observation.
     *
     * @see <a href="https://github.com/gradle/gradle/issues/20581">issue</a>
     */
    protected void checkNotObserved() {
        if (cachedVariants != null) {
            throw new MetadataModificationException("Gradle Module Metadata can't be modified after an eagerly populated publication.");
        }
    }

    public static final class MetadataModificationException extends GradleException implements ResolutionProvider {
        public MetadataModificationException(String message) {
            super(message);
        }

        @Override
        public List<String> getResolutions() {
            return Collections.singletonList(Documentation.upgradeMinorGuide(8, "gmm_modification_after_publication_populated").getConsultDocumentationMessage());
        }
    }

    private static final class ConfigurationVariantAction {

        private final Supplier<Configuration> configuration;
        private final Action<? super ConfigurationVariantDetails> spec;
        private final boolean mutate;

        public ConfigurationVariantAction(Supplier<Configuration> configuration, Action<? super ConfigurationVariantDetails> spec, boolean mutate) {
            this.configuration = configuration;
            this.spec = spec;
            this.mutate = mutate;
        }

        public Configuration getConfiguration() {
            return configuration.get();
        }

        public Action<? super ConfigurationVariantDetails> getSpec() {
            return spec;
        }

        public boolean isMutate() {
            return mutate;
        }

    }

}
