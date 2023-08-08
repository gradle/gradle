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
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;

import java.util.Map;
import java.util.Set;

public class DefaultAdhocSoftwareComponent implements AdhocComponentWithVariants, SoftwareComponentInternal {
    private final String componentName;
    private final Map<Configuration, ConfigurationVariantMapping> variants = Maps.newLinkedHashMapWithExpectedSize(4);
    private final Instantiator instantiator;

    private Set<UsageContext> cachedVariants;

    public DefaultAdhocSoftwareComponent(String componentName, Instantiator instantiator) {
        this.componentName = componentName;
        this.instantiator = instantiator;
    }

    @Override
    public String getName() {
        return componentName;
    }

    @Override
    public void addVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> spec) {
        checkNotObserved();
        variants.put(outgoingConfiguration, new ConfigurationVariantMapping((ConfigurationInternal) outgoingConfiguration, spec, instantiator));
    }

    @Override
    public void withVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> action) {
        checkNotObserved();
        if (!variants.containsKey(outgoingConfiguration)) {
            throw new InvalidUserDataException("Variant for configuration " + outgoingConfiguration.getName() + " does not exist in component " + componentName);
        }
        variants.get(outgoingConfiguration).addAction(action);
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        if (cachedVariants == null) {
            ImmutableSet.Builder<UsageContext> builder = new ImmutableSet.Builder<>();
            for (ConfigurationVariantMapping variant : variants.values()) {
                variant.collectVariants(builder);
            }
            cachedVariants = builder.build();
        }

        return cachedVariants;
    }

    protected boolean isRegisteredAsLegacyVariant(Configuration outgoingConfiguration) {
        return variants.containsKey(outgoingConfiguration);
    }

    /**
     * Ensure this component cannot be modified after observation.
     *
     * @see <a href="https://github.com/gradle/gradle/issues/20581">issue</a>
     */
    protected void checkNotObserved() {
        if (cachedVariants != null) {
            DeprecationLogger.deprecateBehaviour("Gradle Module Metadata is modified after an eagerly populated publication.")
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(8, "gmm_modification_after_publication_populated")
                .nagUser();
        }
    }
}
