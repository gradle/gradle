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

package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.model.MutableComponentVariant;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;

public class GradleModuleMetadataCompatibilityConverter {

    private static final Attribute<String> USAGE_STRING_ATTRIBUTE = Attribute.of(Usage.USAGE_ATTRIBUTE.getName(), String.class);
    private static final Attribute<String> LIBRARY_ELEMENTS_STRING_ATTRIBUTE = Attribute.of(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.getName(), String.class);

    private ImmutableAttributesFactory attributesFactory;
    private NamedObjectInstantiator instantiator;

    public GradleModuleMetadataCompatibilityConverter(ImmutableAttributesFactory attributesFactory, NamedObjectInstantiator instantiator) {
        this.attributesFactory = attributesFactory;
        this.instantiator = instantiator;
    }

    public void process(MutableModuleComponentResolveMetadata metaDataFromResource) {
        // This code path will always be a no-op following the changes in DefaultImmutableAttributesFactory
        // However this code will have to remain forever while the other one should be removed at some point (Gradle 7.0?)
        for (MutableComponentVariant variant : metaDataFromResource.getMutableVariants()) {
            ImmutableAttributes attributes = variant.getAttributes();
            ImmutableAttributes updatedAttributes = ImmutableAttributes.EMPTY;
            if (attributes.contains(USAGE_STRING_ATTRIBUTE)) {
                String attributeValue = attributes.getAttribute(USAGE_STRING_ATTRIBUTE);
                if (attributeValue.endsWith("-jars")) {
                    updatedAttributes = attributesFactory.concat(updatedAttributes, USAGE_STRING_ATTRIBUTE, new CoercingStringValueSnapshot(attributeValue.replace("-jars", ""), instantiator));
                }
            }
            if (!updatedAttributes.isEmpty() && !attributes.contains(LIBRARY_ELEMENTS_STRING_ATTRIBUTE)) {
                updatedAttributes = attributesFactory.concat(updatedAttributes, LIBRARY_ELEMENTS_STRING_ATTRIBUTE, new CoercingStringValueSnapshot(LibraryElements.JAR, instantiator));
            }
            if (!updatedAttributes.isEmpty()) {
                updatedAttributes = attributesFactory.concat(attributes, updatedAttributes);
                variant.setAttributes(updatedAttributes);
            }
        }
    }
}
