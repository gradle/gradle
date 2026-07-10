/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.jspecify.annotations.NullMarked;
import org.junit.runner.Description;

import java.util.Collections;
import java.util.Map;

/**
 * A set of {@link Description} instances. This class is similar to {@link Collections#newSetFromMap(Map)},
 * but for a {@link DescriptionMap} which can't be passed to that method.
 */
@NullMarked
public final class DescriptionSet {
    /**
     * A witness that allows using the {@link Description} itself for the value and the wrapped value.
     */
    private static final DescriptionMap.DescriptionWitness<Description, Description> DESCRIPTION_SELF_WITNESS =
        new DescriptionMap.DescriptionWitness<Description, Description>() {
            @Override
            public Description getDescription(Description wrappedValue) {
                return wrappedValue;
            }

            @Override
            public Description getValue(Description wrappedValue) {
                return wrappedValue;
            }
        };

    private final DescriptionMap<Description, Description> descriptionMap =
        new DescriptionMap<>((desc, valueDesc) -> valueDesc, DESCRIPTION_SELF_WITNESS);

    /**
     * Adds the given description to this set.
     *
     * @param description the description to add
     */
    public void add(Description description) {
        descriptionMap.put(description, description);
    }

    /**
     * Returns true if this set contains the given description, possibly using identity to distinguish
     * between descriptions with colliding IDs.
     *
     * @param description the description to check
     * @return true if this set contains the given description
     */
    public boolean contains(Description description) {
        return descriptionMap.get(description) != null;
    }

    /**
     * Removes the given description from this set, possibly using identity to distinguish
     * between descriptions with colliding IDs.
     *
     * @param description the description to remove
     * @return true if the description was present and removed, false otherwise
     */
    public boolean remove(Description description) {
        return descriptionMap.remove(description) != null;
    }

    /**
     * Clears this set.
     */
    public void clear() {
        descriptionMap.clear();
    }

    /**
     * Returns true if this set is empty.
     *
     * @return true if this set is empty
     */
    public boolean isEmpty() {
        return descriptionMap.isEmpty();
    }
}
