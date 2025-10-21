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

package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Converts legacy {@link Usage} values to their modern equivalents.
 * <p>
 * TODO: This functionality should not be directly integrated in the attributes container, but
 * should be handled externally. This is mostly needed to translate legacy external attributes
 * to their modern counterparts, meaning we only need this when reading attributes from
 * GMM. We should deprecate using the legacy usage values for all other use cases, then remove
 * this class.
 */
class UsageCompatibilityHandler {
    private final IsolatableFactory isolatableFactory;
    private final NamedObjectInstantiator instantiator;

    UsageCompatibilityHandler(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
        this.isolatableFactory = isolatableFactory;
        this.instantiator = instantiator;
    }

    public <T> ImmutableAttributes doConcat(DefaultAttributesFactory factory, ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        factory.assertAttributeNotAlreadyPresent(node, key);

        assert key.getName().equals(Usage.USAGE_ATTRIBUTE.getName()) : "Should only be invoked for 'org.gradle.usage', got '" + key.getName() + "'";

        if (value instanceof CoercingStringValueSnapshot) {
            String val = ((CoercingStringValueSnapshot) value).getValue();
            String replacementUsage = getReplacementUsage(val);
            String libraryElements = getLibraryElements(val);
            if (replacementUsage == null || libraryElements == null) {
                return factory.doConcatEntry(node, new DefaultImmutableAttributesEntry<>(key, value));
            }

            Attribute<String> typedAttribute = getAs(key, String.class);
            Isolatable<String> coercingStringValueSnapshot = new CoercingStringValueSnapshot(replacementUsage, instantiator);
            ImmutableAttributes usageNode = factory.doConcatEntry(node, new DefaultImmutableAttributesEntry<>(typedAttribute, coercingStringValueSnapshot));
            return factory.doConcatEntry(usageNode, new DefaultImmutableAttributesEntry<>(Attribute.of(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.getName(), String.class), new CoercingStringValueSnapshot(libraryElements, instantiator)));
        } else {
            String val = Objects.requireNonNull(value.isolate()).toString();
            String replacementUsage = getReplacementUsage(val);
            String libraryElements = getLibraryElements(val);
            if (replacementUsage == null || libraryElements == null) {
                return factory.doConcatEntry(node, new DefaultImmutableAttributesEntry<>(key, value));
            }

            Attribute<Usage> typedAttribute = getAs(key, Usage.class);
            Isolatable<Usage> isolate = isolatableFactory.isolate(instantiator.named(Usage.class, replacementUsage));
            ImmutableAttributes usageNode = factory.doConcatEntry(node, new DefaultImmutableAttributesEntry<>(typedAttribute, isolate));
            return factory.doConcatEntry(usageNode, new DefaultImmutableAttributesEntry<>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, isolatableFactory.isolate(instantiator.named(LibraryElements.class, libraryElements))));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Attribute<T> getAs(Attribute<?> key, Class<T> type) {
        assert key.getType() == type : "Attribute type must be a " + type;
        return (Attribute<T>) key;
    }

    @Nullable String getReplacementUsage(String usage) {
        if (usage.endsWith("-jars")) {
            return usage.replace("-jars", "");
        } else if (usage.endsWith("-classes")) {
            return usage.replace("-classes", "");
        } else if (usage.endsWith("-resources")) {
            return usage.replace("-resources", "");
        }

        return null;
    }

    @Nullable String getLibraryElements(String usage) {
        if (usage.endsWith("-jars")) {
            return LibraryElements.JAR;
        } else if (usage.endsWith("-classes")) {
            return LibraryElements.CLASSES;
        } else if (usage.endsWith("-resources")) {
            return LibraryElements.RESOURCES;
        }

        return null;
    }
}
