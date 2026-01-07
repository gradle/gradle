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
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ConfigurationStateDB {

    public static final class ConsistentResolution {
        public final ConfigurationInternal versionsSource;
        public final String reason;

        public ConsistentResolution(ConfigurationInternal versionsSource, String reason) {
            this.versionsSource = versionsSource;
            this.reason = reason;
        }
    }

    private final ArrayList<String> ids = new ArrayList<>();
    private final Int2ObjectOpenHashMap<List<String>> declarationAlternatives = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<List<String>> resolutionAlternatives = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<String> descriptions = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ConsistentResolution> consistentResolutions = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<FileCollectionInternal> intrinsicFiles = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ResolutionStrategyInternal> resolutionStrategies = new Int2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap copyCounts = new Int2IntOpenHashMap();
    private final IntOpenHashSet nonVisible = new IntOpenHashSet();
    private final IntOpenHashSet nonTransitive = new IntOpenHashSet();

    public int register(String name) {
        int id = ids.size();
        ids.add(name);
        return id;
    }

    public String getName(int id) {
        return ids.get(id);
    }

    public List<String> getDeclarationAlternatives(int configuration) {
        return declarationAlternatives.getOrDefault(configuration, ImmutableList.of());
    }

    public List<String> getResolutionAlternatives(int configuration) {
        return resolutionAlternatives.getOrDefault(configuration, ImmutableList.of());
    }

    public void addDeclarationAlternatives(int files, String[] alternativesForDeclaring) {
        declarationAlternatives.compute(files, (k, v) -> concat(v, alternativesForDeclaring));
    }

    public void addResolutionAlternatives(int files, String[] alternativesForResolving) {
        resolutionAlternatives.compute(files, (k, v) -> concat(v, alternativesForResolving));
    }

    public @Nullable String getDescription(int configuration) {
        return descriptions.get(configuration);
    }

    public void setDescription(int configuration, @Nullable String description) {
        if (description == null) {
            descriptions.remove(configuration);
        } else {
            descriptions.put(configuration, description);
        }
    }

    public void copy(int from, int to) {
        List<String> strings = declarationAlternatives.get(from);
        if (strings != null) {
            declarationAlternatives.put(to, strings);
        }
        strings = resolutionAlternatives.get(from);
        if (strings != null) {
            resolutionAlternatives.put(to, strings);
        }
        String description = descriptions.get(from);
        if (description != null) {
            descriptions.put(to, description);
        }
        if (nonVisible.contains(from)) {
            nonVisible.add(to);
        }
        if (nonTransitive.contains(from)) {
            nonTransitive.add(to);
        }
    }

    public FileCollectionInternal getIntrinsicFiles(int configuration, Supplier<FileCollectionInternal> supplier) {
        return intrinsicFiles.computeIfAbsent(configuration, k -> supplier.get());
    }

    public void enableConsistentResolution(int configuration, ConfigurationInternal versionsSource) {
        consistentResolutions.put(configuration, new ConsistentResolution(versionsSource, "version resolved in " + versionsSource + " by consistent resolution"));
    }

    public @Nullable ConfigurationInternal getConsistentResolutionSource(int configuration) {
        ConsistentResolution consistentResolution = getConsistentResolution(configuration);
        return consistentResolution != null ? consistentResolution.versionsSource : null;
    }

    public @Nullable ConsistentResolution getConsistentResolution(int configuration) {
        return consistentResolutions.get(configuration);
    }

    public void disableConsistentResolution(int configuration) {
        consistentResolutions.remove(configuration);
    }

    public ResolutionStrategyInternal getResolutionStrategy(int id) {
        return resolutionStrategies.get(id);
    }

    public ResolutionStrategyInternal produceResolutionStrategy(int id, Supplier<ResolutionStrategyInternal> supplier) {
        return resolutionStrategies.computeIfAbsent(id, k -> supplier.get());
    }

    public int incrementAndGetCopyCount(int id) {
        return copyCounts.compute(id, (k, v) -> (v != null ? v : 0) + 1);
    }

    public boolean getVisible(int id) {
        return !nonVisible.contains(id);
    }

    public void setVisible(int id, boolean value) {
        if (value) {
            nonVisible.remove(id);
        } else {
            nonVisible.add(id);
        }
    }

    public boolean getTransitive(int id) {
        return !nonTransitive.contains(id);
    }

    public void setTransitive(int id, boolean value) {
        if (value) {
            nonTransitive.remove(id);
        } else {
            nonTransitive.add(id);
        }
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
}
