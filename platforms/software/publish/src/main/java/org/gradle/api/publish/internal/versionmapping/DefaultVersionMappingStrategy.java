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
package org.gradle.api.publish.internal.versionmapping;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.VariantVersionMappingStrategy;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultVersionMappingStrategy implements VersionMappingStrategyInternal {
    private final ObjectFactory objectFactory;
    private final ConfigurationContainer configurations;
    private final AttributesSchemaInternal schema;
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;

    private final List<Action<? super VariantVersionMappingStrategy>> mappingsForAllVariants = new ArrayList<>(2);
    private final Map<ImmutableAttributes, String> defaultConfigurations = new HashMap<>();
    private final Multimap<ImmutableAttributes, Action<? super VariantVersionMappingStrategy>> attributeBasedMappings = ArrayListMultimap.create();

    private AttributeMatcher matcher;

    @Inject
    public DefaultVersionMappingStrategy(
        ObjectFactory objectFactory,
        ConfigurationContainer configurations,
        AttributesSchemaInternal schema,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices
    ) {
        this.objectFactory = objectFactory;
        this.configurations = configurations;
        this.schema = schema;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
    }

    @Override
    public void allVariants(Action<? super VariantVersionMappingStrategy> action) {
        mappingsForAllVariants.add(action);
    }

    @Override
    public <T> void variant(Attribute<T> attribute, T attributeValue, Action<? super VariantVersionMappingStrategy> action) {
        attributeBasedMappings.put(attributesFactory.of(attribute, attributeValue), action);
    }

    @Override
    public void usage(String usage, Action<? super VariantVersionMappingStrategy> action) {
        variant(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, usage), action);
    }

    @Override
    public void defaultResolutionConfiguration(String usage, String defaultConfiguration) {
        defaultConfigurations.put(attributesFactory.of(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, usage)), defaultConfiguration);
    }

    @Override
    public VariantVersionMappingStrategyInternal findStrategyForVariant(ImmutableAttributes variantAttributes) {
        DefaultVariantVersionMappingStrategy strategy = createDefaultMappingStrategy(variantAttributes);
        // Apply strategies for "all variants"
        for (Action<? super VariantVersionMappingStrategy> action : mappingsForAllVariants) {
            action.execute(strategy);
        }

        // Then use attribute specific mapping
        if (!attributeBasedMappings.isEmpty()) {
            Set<ImmutableAttributes> candidates = attributeBasedMappings.keySet();
            List<ImmutableAttributes> matches = getMatcher().matchMultipleCandidates(candidates, variantAttributes);
            if (matches.size() == 1) {
                Collection<Action<? super VariantVersionMappingStrategy>> actions = attributeBasedMappings.get(matches.get(0));
                for (Action<? super VariantVersionMappingStrategy> action : actions) {
                    action.execute(strategy);
                }
            } else if (matches.size() > 1) {
                throw new InvalidUserCodeException("Unable to find a suitable version mapping strategy for " + variantAttributes);
            }
        }
        return strategy;
    }

    private DefaultVariantVersionMappingStrategy createDefaultMappingStrategy(ImmutableAttributes variantAttributes) {
        DefaultVariantVersionMappingStrategy strategy = new DefaultVariantVersionMappingStrategy(configurations);
        if (!defaultConfigurations.isEmpty()) {
            // First need to populate the default variant version mapping strategy with the default values
            // provided by plugins
            Set<ImmutableAttributes> candidates = defaultConfigurations.keySet();
            List<ImmutableAttributes> matches = getMatcher().matchMultipleCandidates(candidates, variantAttributes);
            for (ImmutableAttributes match : matches) {
                strategy.setDefaultResolutionConfiguration(configurations.getByName(defaultConfigurations.get(match)));
            }
        }
        return strategy;
    }

    private AttributeMatcher getMatcher() {
        if (matcher == null) {
            ImmutableAttributesSchema immutableSchema = attributeSchemaServices.getSchemaFactory().create(schema);
            matcher = attributeSchemaServices.getMatcher(immutableSchema, ImmutableAttributesSchema.EMPTY);
        }

        return matcher;
    }

}
