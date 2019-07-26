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

class UsageCompatibilityHandler {
    private final IsolatableFactory isolatableFactory;
    private final NamedObjectInstantiator instantiator;

    UsageCompatibilityHandler(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
        this.isolatableFactory = isolatableFactory;
        this.instantiator = instantiator;
    }

    public <T> ImmutableAttributes doConcat(DefaultImmutableAttributesFactory factory, ImmutableAttributes node, Attribute<T> key, Isolatable<T> value) {
        assert key.getName().equals(Usage.USAGE_ATTRIBUTE.getName()) : "Should only be invoked for 'org.gradle.usage', got '" + key.getName() + "'";
        // Replace deprecated usage values
        String val;
        boolean typedUsage = false;
        if (value instanceof CoercingStringValueSnapshot) {
            val = ((CoercingStringValueSnapshot) value).getValue();
        } else {
            typedUsage = true;
            val = value.isolate().toString();
        }
        // TODO Add a deprecation warning in Gradle 6.0
        if (val.endsWith("-jars")) {
            return doConcatWithReplacement(factory, node, key, typedUsage, val.replace("-jars", ""), LibraryElements.JAR);
        } else if (val.endsWith("-classes")) {
            return doConcatWithReplacement(factory, node, key, typedUsage, val.replace("-classes", ""), LibraryElements.CLASSES);
        } else if (val.endsWith("-resources")) {
            return doConcatWithReplacement(factory, node, key, typedUsage, val.replace("-resources", ""), LibraryElements.RESOURCES);
        } else {
            return factory.doConcatIsolatable(node, key, value);
        }

    }

    private <T> ImmutableAttributes doConcatWithReplacement(DefaultImmutableAttributesFactory factory, ImmutableAttributes node, Attribute<T> key, boolean typedUsage, String usage, String libraryElements) {
        if (typedUsage) {
            ImmutableAttributes usageNode = factory.doConcatIsolatable(node, key, isolatableFactory.isolate(instantiator.named(Usage.class, usage)));
            return factory.doConcatIsolatable(usageNode, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, isolatableFactory.isolate(instantiator.named(LibraryElements.class, libraryElements)));
        } else {
            ImmutableAttributes usageNode = factory.doConcatIsolatable(node, key, new CoercingStringValueSnapshot(usage, instantiator));
            return factory.doConcatIsolatable(usageNode, Attribute.of(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.getName(), String.class), new CoercingStringValueSnapshot(libraryElements, instantiator));
        }
    }
}
