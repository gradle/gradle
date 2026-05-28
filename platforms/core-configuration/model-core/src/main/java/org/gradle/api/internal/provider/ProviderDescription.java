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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A structural description of a {@link ProviderInternal} for diagnostics and provenance.
 *
 * <p>Returned by {@link ProviderInternal#explain(boolean)}. Models the provider tree as a flat
 * type with a {@link Kind} discriminator plus a recursive {@link #sources()} list, rather than
 * a sealed hierarchy of subtypes.</p>
 */
public final class ProviderDescription {

    public enum Kind {
        PROPERTY,
        LIST_PROPERTY,
        SET_PROPERTY,
        MAP_PROPERTY,
        VALUE_SOURCE,
        MAPPED,
        FLAT_MAPPED,
        FILTERED,
        OR_ELSE,
        ZIP,
        FIXED,
        UNKNOWN
    }

    private final Kind kind;
    private final boolean hasValue;
    @Nullable private final String displayName;
    private final List<ProviderDescription> sources;
    private final Map<String, Object> kindSpecificData;

    public ProviderDescription(
        Kind kind,
        boolean hasValue,
        @Nullable String displayName,
        List<ProviderDescription> sources,
        Map<String, Object> kindSpecificData
    ) {
        this.kind = kind;
        this.hasValue = hasValue;
        this.displayName = displayName;
        this.sources = ImmutableList.copyOf(sources);
        this.kindSpecificData = ImmutableMap.copyOf(kindSpecificData);
    }

    public Kind kind() {
        return kind;
    }

    public boolean hasValue() {
        return hasValue;
    }

    @Nullable
    public String displayName() {
        return displayName;
    }

    /**
     * Direct sources of this provider. Empty for leaves; one entry for single-source derived
     * providers ({@code map}, {@code flatMap}, {@code filter}); many for {@code orElse}, variadic
     * {@code zip}, and collection-property contributors.
     */
    public List<ProviderDescription> sources() {
        return sources;
    }

    /**
     * Kind-specific data bag. Populated only for kinds that have such data
     * (e.g. {@code valueSourceType} for {@link Kind#VALUE_SOURCE}, {@code baseType} for
     * {@link Kind#PROPERTY}).
     */
    public Map<String, Object> kindSpecificData() {
        return kindSpecificData;
    }

    public static ProviderDescription unknown(@Nullable String displayName, boolean hasValue) {
        return new ProviderDescription(
            Kind.UNKNOWN,
            hasValue,
            displayName,
            ImmutableList.of(),
            ImmutableMap.of()
        );
    }

    /**
     * Renders this description into a {@link TreeFormatter} as a missing-value derivation chain.
     *
     * <p>Rendering rule: descend into every source whose {@link #hasValue()} is {@code false}, in
     * source-declaration order; skip present sources entirely. For multi-source derived providers
     * ({@code orElse}, variadic {@code zip}) and collection properties this surfaces every missing
     * branch in one shot, matching the behaviour of the existing {@code pushWhenMissing} chain.</p>
     *
     * <p>Returns {@code true} if anything was rendered, {@code false} if the description is too
     * trivial to add information to the message (e.g. a single {@link Kind#UNKNOWN} node with no
     * sources).</p>
     */
    public boolean renderMissingChainTo(TreeFormatter formatter) {
        if (kind == Kind.UNKNOWN && sources.isEmpty() && kindSpecificData.isEmpty()) {
            return false;
        }
        renderNode(this, formatter);
        return true;
    }

    private static void renderNode(ProviderDescription desc, TreeFormatter formatter) {
        formatter.node(describeNode(desc));
        boolean hasMissingSource = anyMissing(desc.sources);
        if (!desc.kindSpecificData.isEmpty() || hasMissingSource) {
            formatter.startChildren();
            desc.kindSpecificData.forEach((key, value) ->
                formatter.node(key).append(": ").append(String.valueOf(value)));
            for (ProviderDescription source : desc.sources) {
                if (!source.hasValue) {
                    renderNode(source, formatter);
                }
            }
            formatter.endChildren();
        }
    }

    private static String describeNode(ProviderDescription desc) {
        String kindLabel = desc.kind.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String name = desc.displayName != null ? " '" + desc.displayName + "'" : "";
        return kindLabel + name;
    }

    private static boolean anyMissing(List<ProviderDescription> sources) {
        for (ProviderDescription source : sources) {
            if (!source.hasValue) {
                return true;
            }
        }
        return false;
    }
}
