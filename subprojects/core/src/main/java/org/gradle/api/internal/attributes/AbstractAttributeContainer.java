/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Common base class for {@link AttributeContainerInternal} which enforces implementation of
 * {@link #hashCode} and {@link #equals}.
 */
public abstract class AbstractAttributeContainer implements AttributeContainerInternal {

    @Override
    public Map<Attribute<?>, ?> asMap() {
        return asImmutable().asMap();
    }

    @Override
    public boolean isEmpty() {
        return keySet().isEmpty();
    }

    @Override
    public boolean contains(Attribute<?> key) {
        return keySet().contains(key);
    }

    @Override
    public AttributeContainer getAttributes() {
        return this;
    }

    @Override
    public abstract boolean equals(Object o);
    @Override
    public abstract int hashCode();

    /**
     * Verifies that the given key is a valid attribute request.
     * <p>
     * Requesting null keys is not allowed, and is deprecated.
     *
     * @param key the requested attribute to validate
     * @return {@code true} is valid request; {@code false} otherwise
     */
    protected boolean isValidAttributeRequest(@Nullable Attribute<?> key) {
        if (key == null) {
            DeprecationLogger.deprecateBehaviour("Retrieving attribute with a null key.")
                .withAdvice("Don't request attributes from attribute containers using null keys.")
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(8, "null-attribute-lookup")
                .nagUser();
            return false;
        } else {
            return true;
        }
    }
}
