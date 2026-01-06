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

package org.gradle.api.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;

public final class ConfigurationStateDB {
    private final IdentityHashMap<Configuration, List<String>> declarationAlternatives = new IdentityHashMap<>();
    private final IdentityHashMap<Configuration, List<String>> resolutionAlternatives = new IdentityHashMap<>();

    public List<String> getDeclarationAlternatives(Configuration configuration) {
        return declarationAlternatives.getOrDefault(configuration, ImmutableList.of());
    }

    public List<String> getResolutionAlternatives(Configuration configuration) {
        return resolutionAlternatives.getOrDefault(configuration, ImmutableList.of());
    }

    public void addDeclarationAlternatives(DefaultConfiguration files, String[] alternativesForDeclaring) {
        declarationAlternatives.compute(files, (k, v) -> concat(v, alternativesForDeclaring));
    }

    public void addResolutionAlternatives(DefaultConfiguration files, String[] alternativesForResolving) {
        resolutionAlternatives.compute(files, (k, v) -> concat(v, alternativesForResolving));
    }

    private static List<String> concat(@Nullable List<String> v, String[] alternativesForResolving) {
        if (v == null) {
            return ImmutableList.copyOf(alternativesForResolving);
        } else {
            ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(v.size() + alternativesForResolving.length);
            builder.addAll(v);
            builder.add(alternativesForResolving);
            return builder.build();
        }
    }

    public void copy(Configuration from, Configuration to) {
        List<String> strings = declarationAlternatives.get(from);
        if (strings != null) {
            declarationAlternatives.put(to, strings);
        }
        strings = resolutionAlternatives.get(from);
        if (strings != null) {
            resolutionAlternatives.put(to, strings);
        }
    }
}
