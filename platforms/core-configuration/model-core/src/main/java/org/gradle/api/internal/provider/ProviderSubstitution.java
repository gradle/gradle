/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.Cast;

import java.util.IdentityHashMap;
import java.util.function.Supplier;

/**
 * Performs an identity-based, path-copying substitution over structurally visible provider nodes.
 */
final class ProviderSubstitution {
    private final ProviderInternal<?> target;
    private final Supplier<? extends ProviderInternal<?>> replacementFactory;
    private final IdentityHashMap<ProviderInternal<?>, ProviderInternal<?>> substitutions = new IdentityHashMap<>();
    private ProviderInternal<?> replacement;

    ProviderSubstitution(ProviderInternal<?> target, Supplier<? extends ProviderInternal<?>> replacementFactory) {
        this.target = target;
        this.replacementFactory = replacementFactory;
    }

    /**
     * Substitutes all structurally visible occurrences of the target in the given provider.
     * The replacement is created lazily and at most once.
     */
    <T> ProviderInternal<T> substitute(ProviderInternal<T> provider) {
        if (provider == target) {
            return Cast.uncheckedCast(replacement());
        }

        ProviderInternal<?> substituted = substitutions.get(provider);
        if (substituted != null) {
            return Cast.uncheckedCast(substituted);
        }

        if (!(provider instanceof StructuralProvider<?>)) {
            return provider;
        }

        ProviderInternal<T> result = Cast.<StructuralProvider<T>>uncheckedCast(provider).substitute(this);
        substitutions.put(provider, result);
        return result;
    }

    private ProviderInternal<?> replacement() {
        if (replacement == null) {
            replacement = replacementFactory.get();
        }
        return replacement;
    }
}
