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
package org.gradle.api.publish;

import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.HasInternalProtocol;

/**
 * The version mapping strategy for a publication. By default,
 * Gradle will use the declared versions of a dependency directly. However
 * in some situations it might be better to publish the resolved versions, or both
 * when the metadata format supports it.
 *
 * @since 5.2
 */
@HasInternalProtocol
public interface VersionMappingStrategy {
    /**
     * Configures the version mapping strategy for all variants
     * @param action the configuration action
     */
    void allVariants(Action<? super VariantVersionMappingStrategy> action);

    /**
     * Configures the version mapping strategy for the variant which matches the provided
     * attribute value.
     * @param attribute the attribute to find
     * @param attributeValue the attribute value
     * @param action the configuration action
     */
    <T> void variant(Attribute<T> attribute, T attributeValue, Action<? super VariantVersionMappingStrategy> action);

    /**
     * A short hand method to configure the variants which matches the provided Usage attribute.
     * This is the recommended way to configure the mapping strategy for the general case.
     * @param usage the usage to look for
     * @param action the configuration action
     */
    void usage(String usage, Action<? super VariantVersionMappingStrategy> action);
}
