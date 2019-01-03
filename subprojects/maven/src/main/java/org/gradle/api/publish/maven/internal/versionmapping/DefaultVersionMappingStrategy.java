/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.publish.maven.internal.versionmapping;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.VariantVersionMappingStrategy;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.List;

public class DefaultVersionMappingStrategy implements VersionMappingStrategyInternal {
    private final ObjectFactory objectFactory;
    private final ConfigurationContainer configurations;
    private final List<PublishedVariantVersionMapping> mappings = Lists.newArrayListWithExpectedSize(2);

    public DefaultVersionMappingStrategy(ObjectFactory objectFactory, ConfigurationContainer configurations) {
        this.objectFactory = objectFactory;
        this.configurations = configurations;
    }

    @Override
    public void allVariants(Action<? super VariantVersionMappingStrategy> action) {
        PublishedVariantVersionMapping mapping = new PublishedVariantVersionMapping(Specs.SATISFIES_ALL, action);
        mappings.add(mapping);
    }

    @Override
    public <T> void variant(Attribute<T> attribute, T attributeValue, Action<? super VariantVersionMappingStrategy> action) {
        PublishedVariantVersionMapping mapping = new PublishedVariantVersionMapping(new Spec<PublishedVariant>() {
            @Override
            public boolean isSatisfiedBy(PublishedVariant element) {
                AttributeValue<T> entry = element.attributes.findEntry(attribute);
                return entry.isPresent() && entry.get() == attributeValue;
            }
        }, action);
        mappings.add(mapping);
    }

    @Override
    public void usage(String usage, Action<? super VariantVersionMappingStrategy> action) {
        variant(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, usage), action);
    }

    @Override
    public VariantVersionMappingStrategyInternal findStrategyForVariant(String variantName, ImmutableAttributes variantAttributes) {
        PublishedVariant publishedVariant = new PublishedVariant(variantName, variantAttributes);
        DefaultVariantVersionMappingStrategy strategy = new DefaultVariantVersionMappingStrategy(configurations);
        for (PublishedVariantVersionMapping mapping : mappings) {
            mapping.applyTo(publishedVariant, strategy);
        }
        return strategy;
    }
}
