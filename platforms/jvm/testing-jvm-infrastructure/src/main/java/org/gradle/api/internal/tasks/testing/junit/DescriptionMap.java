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

import org.gradle.internal.Cast;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * A map from a {@link Description} to some value.
 *
 * <p>
 * This class handles the fact that {@link Description}, despite claiming to have a "unique ID",
 * may actually share the same ID for different descriptions (e.g. parameterized tests with the same name).
 * See <a href="https://github.com/junit-team/junit-framework/blob/r5.14.1/junit-vintage-engine/src/main/java/org/junit/vintage/engine/execution/TestRun.java#L241-L245">this code</a>
 * for how JUnit Vintage handles it.
 * This class ensures that each distinct {@link Description} is treated separately, even if their IDs collide,
 * while also preserving the ability to treat them according to their IDs when not colliding.
 * </p>
 *
 * <p>
 * The reason not to simply use identity-based mapping is to preserve compatibility in the event that a
 * test reports a new {@link Description} instance that is {@link Object#equals(Object) equal} to a previously-reported one.
 * If we were to use identity-based mapping only, such a test might not be able to look up the value for the previously-reported
 * description, leading to confusing behavior.
 * We only want to fall back to identity-based mapping when there is a collision on IDs.
 * </p>
 *
 * <p>
 * This class does not implement the {@link Map} interface due to the complexity of correctly handling
 * the representation of keys and values, especially in the presence of ID collisions.
 * Mappings may vanish if a new mapping is added with a colliding ID because the equality-based mapping
 * is replaced by an identity-based mapping.
 * </p>
 *
 * @param <V> the value type
 * @param <DV> the description-and-value wrapper type
 */
@NullMarked
public final class DescriptionMap<V, DV> {
    /**
     * Given a potentially-wrapped value, provides access to the description and the value.
     */
    @NullMarked
    public interface DescriptionWitness<V, DV> {
        Description getDescription(DV wrappedValue);

        V getValue(DV wrappedValue);
    }

    /**
     * The simple implementation for values that don't store the description.
     *
     * @param <V> the value type
     */
    @NullMarked
    public static final class SimpleValueWrapper<V> {
        private static final DescriptionWitness<Object, SimpleValueWrapper<Object>> WITNESS =
            new DescriptionWitness<Object, SimpleValueWrapper<Object>>() {
                @Override
                public Description getDescription(SimpleValueWrapper<Object> wrappedValue) {
                    return wrappedValue.getDescription();
                }

                @Override
                public Object getValue(SimpleValueWrapper<Object> wrappedValue) {
                    return wrappedValue.getValue();
                }
            };

        public static <V> DescriptionWitness<V, SimpleValueWrapper<V>> witness() {
            // Safe due to invariant nature of witness
            return Cast.uncheckedNonnullCast(WITNESS);
        }

        private final Description description;
        private final V value;

        public SimpleValueWrapper(Description description, V value) {
            this.description = description;
            this.value = value;
        }

        public Description getDescription() {
            return description;
        }

        public V getValue() {
            return value;
        }
    }

    public static <V> DescriptionMap<V, SimpleValueWrapper<V>> createSimple() {
        return new DescriptionMap<>(
            SimpleValueWrapper<V>::new,
            SimpleValueWrapper.witness()
        );
    }

    private final BiFunction<Description, V, DV> descriptionAndValueFactory;
    private final DescriptionWitness<V, DV> witness;
    private final Map<Description, List<DV>> delegate = new LinkedHashMap<>();

    public DescriptionMap(BiFunction<Description, V, DV> descriptionAndValueFactory, DescriptionWitness<V, DV> witness) {
        this.descriptionAndValueFactory = descriptionAndValueFactory;
        this.witness = witness;
    }

    /**
     * Helper method to check if the given description-and-value maps to the given description by identity.
     *
     * @param value the description-and-value
     */
    // Suppress warnings about not using equals() as we specifically need to skip it, see class-level Javadoc
    @SuppressWarnings("ReferenceEquality")
    private boolean isSameDescriptionByIdentity(DV value, Description description) {
        return witness.getDescription(value) == description;
    }

    /**
     * Put the given value for the given description. If there is a collision on IDs,
     * the description is matched by identity.
     *
     * <p>
     * Putting a new value for a description with the same ID as an existing description
     * but different identity will cause both entries to be stored separately.
     * If a value is put for a description that matches an existing description by equality and identity,
     * the existing value is replaced.
     * </p>
     *
     * @param description the description
     * @param value the value
     */
    public void put(Description description, V value) {
        delegate.compute(description, (potentiallyDifferentDesc, list) -> {
            DV descriptionAndValue = descriptionAndValueFactory.apply(description, value);
            if (list == null) {
                // Use a singleton list for the common case of no collisions
                return Collections.singletonList(descriptionAndValue);
            } else if (list.size() == 1) {
                if (isSameDescriptionByIdentity(list.get(0), description)) {
                    // Replace the only entry that matches by identity
                    return Collections.singletonList(descriptionAndValue);
                }
                // Collision with existing entry, add to a new list
                List<DV> newList = new ArrayList<>(list.size() + 1);
                newList.addAll(list);
                newList.add(descriptionAndValue);
                return newList;
            } else {
                for (int i = 0; i < list.size(); i++) {
                    if (isSameDescriptionByIdentity(list.get(i), description)) {
                        // Replace existing entry that matches by identity
                        list.set(i, descriptionAndValue);
                        return list;
                    }
                }
                list.add(descriptionAndValue);
                return list;
            }
        });
    }

    /**
     * Get the value for the given description.
     *
     * <p>
     * If there is a collision on IDs, the description is matched by identity.
     * This may mean that a value is not found even if another description with the same ID exists in the map.
     * </p>
     *
     * @param description the description
     * @return the value, or {@code null} if not found
     */
    @Nullable
    public V get(Description description) {
        List<DV> list = delegate.get(description);
        if (list == null) {
            return null;
        } else if (list.size() == 1) {
            return witness.getValue(list.get(0));
        } else {
            for (DV descriptionAndValue : list) {
                if (isSameDescriptionByIdentity(descriptionAndValue, description)) {
                    return witness.getValue(descriptionAndValue);
                }
            }
            return null;
        }
    }

    /**
     * Get all the values value for the given description using equality on descriptions only.
     *
     * <p>
     * This is primarily used when a fallback lookup is needed when identity-based lookup fails.
     * </p>
     */
    public List<V> getByEquality(Description description) {
        List<DV> list = delegate.get(description);
        if (list == null) {
            return Collections.emptyList();
        } else {
            List<V> values = new ArrayList<>(list.size());
            for (DV descriptionAndValue : list) {
                values.add(witness.getValue(descriptionAndValue));
            }
            return Collections.unmodifiableList(values);
        }
    }

    /**
     * Get the value for the first encountered description that matches the predicate.
     *
     * <p>
     * This iterates over all descriptions, including those with colliding IDs but different identities.
     * </p>
     */
    @Nullable
    public V getFirstMatching(Predicate<Description> predicate) {
        for (List<DV> value : delegate.values()) {
            for (DV descriptionAndValue : value) {
                Description description = witness.getDescription(descriptionAndValue);
                if (predicate.test(description)) {
                    return witness.getValue(descriptionAndValue);
                }
            }
        }
        return null;
    }

    /**
     * Remove the mapping for the given description. If there are multiple mappings with colliding IDs,
     * only the one matching by identity is removed.
     *
     * @param description the description to remove
     * @return the removed value, or {@code null} if not found
     */
    @Nullable
    public V remove(Description description) {
        List<DV> list = delegate.get(description);
        if (list == null) {
            return null;
        } else if (list.size() == 1) {
            // Only one entry, remove the whole list
            delegate.remove(description);
            return witness.getValue(list.get(0));
        } else {
            for (int i = 0; i < list.size(); i++) {
                DV descriptionAndValue = list.get(i);
                if (isSameDescriptionByIdentity(descriptionAndValue, description)) {
                    list.remove(i);
                    return witness.getValue(descriptionAndValue);
                }
            }
            return null;
        }
    }

    /**
     * Clear all mappings in the map.
     */
    public void clear() {
        delegate.clear();
    }

    /**
     * Get the only value in the map, if there is exactly one mapping.
     *
     * @return the only value, or {@code null} if there are zero or multiple mappings
     */
    @Nullable
    public V getOnlyValue() {
        V onlyValue = null;
        for (List<DV> valueList : delegate.values()) {
            for (DV descriptionAndValue : valueList) {
                if (onlyValue != null) {
                    return null;
                }
                onlyValue = witness.getValue(descriptionAndValue);
            }
        }
        return onlyValue;
    }

    /**
     * Check if the map is empty.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * Perform the given action for each mapping in the map.
     *
     * <p>
     * This will invoke the action for all key-value pairs, including those with colliding IDs but different identities.
     * </p>
     *
     * @param action the action to perform
     */
    public void forEach(BiConsumer<? super Description, ? super V> action) {
        for (List<DV> valueList : delegate.values()) {
            for (DV descriptionAndValue : valueList) {
                action.accept(witness.getDescription(descriptionAndValue), witness.getValue(descriptionAndValue));
            }
        }
    }
}
