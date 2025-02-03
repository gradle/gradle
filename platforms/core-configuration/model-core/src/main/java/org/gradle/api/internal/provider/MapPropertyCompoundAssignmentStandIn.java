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

package org.gradle.api.internal.provider;

import org.gradle.api.internal.groovy.support.CompoundAssignmentTransformer;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.api.internal.lambdas.SerializableLambdas.bifunction;
import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

/**
 * This class acts as a replacement to call {@code +} on when evaluating {@code DefaultMapProperty += <RHS>} expressions in Groovy code.
 *
 * <b>This is a hidden public API</b>. Compiling Groovy code that depends on Gradle API may end up emitting references to methods of this class.
 *
 * @see CompoundAssignmentTransformer
 */
public final class MapPropertyCompoundAssignmentStandIn<K, V> {
    private final MapProperty<K, V> lhs;

    MapPropertyCompoundAssignmentStandIn(MapProperty<K, V> lhs) {
        this.lhs = lhs;
    }

    // Called for property += Map<K,V>
    public Provider<Map<K, V>> plus(Map<K, V> rhs) {
        return new CollectionPropertyCompoundAssignmentResult<>(
            Providers.internal(lhs.map(transformer(left -> concat(left, rhs)))),
            lhs,
            () -> lhs.putAll(rhs)
        );
    }

    // Called for property += Provider<Map<K,V>>
    public Provider<Map<K, V>> plus(Provider<? extends Map<K, V>> rhs) {
        return new CollectionPropertyCompoundAssignmentResult<>(
            Providers.internal(lhs.zip(rhs, bifunction(MapPropertyCompoundAssignmentStandIn::concat))),
            lhs,
            () -> lhs.putAll(rhs)
        );
    }

    private static <K, V> Map<K, V> concat(Map<? extends K, ? extends V> left, Map<? extends K, ? extends V> right) {
        Map<K, V> result = new LinkedHashMap<>(left);
        result.putAll(right);
        return result;
    }
}
