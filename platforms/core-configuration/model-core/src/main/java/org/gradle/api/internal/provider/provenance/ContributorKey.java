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

package org.gradle.api.internal.provider.provenance;

import org.gradle.internal.code.UserCodeSource;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Stable identity of whoever caused a property mutation.
 * <p>
 * This is deliberately not a runtime application identity and not a source location. Two applications of the
 * same plugin share one contributor key, while two different plugins that happen to mutate a property from the
 * same helper class do not.
 */
public final class ContributorKey {

    public enum Kind {
        /**
         * A binary plugin applied by ID.
         */
        PLUGIN,
        /**
         * A binary plugin applied by class, with no ID available.
         */
        PLUGIN_CLASS,
        /**
         * A build, settings or init script authored as part of the build.
         */
        BUILD_AUTHOR,
        /**
         * A script plugin applied to some target.
         */
        SCRIPT_PLUGIN,
        /**
         * No user code was active when the mutation happened.
         */
        UNKNOWN
    }

    public static final ContributorKey UNKNOWN = new ContributorKey(Kind.UNKNOWN, "");

    /**
     * All build scripts collapse to a single contributor: the build author. Which particular script it was is
     * origin information, not contributor identity.
     */
    public static final ContributorKey BUILD_AUTHOR = new ContributorKey(Kind.BUILD_AUTHOR, "");

    private final Kind kind;
    private final String id;

    private ContributorKey(Kind kind, String id) {
        this.kind = kind;
        this.id = id;
    }

    /**
     * Derives the contributor from the user code that is currently being applied.
     */
    public static ContributorKey of(@Nullable UserCodeSource source) {
        if (source instanceof UserCodeSource.Binary) {
            UserCodeSource.Binary binary = (UserCodeSource.Binary) source;
            String pluginId = binary.getPluginId();
            return pluginId != null
                ? new ContributorKey(Kind.PLUGIN, pluginId)
                : new ContributorKey(Kind.PLUGIN_CLASS, binary.getClassName());
        }
        if (source instanceof UserCodeSource.Script) {
            UserCodeSource.Script script = (UserCodeSource.Script) source;
            if (script.isTopLevelScript()) {
                return BUILD_AUTHOR;
            }
            return new ContributorKey(Kind.SCRIPT_PLUGIN, script.getUri() != null ? script.getUri().toString() : "");
        }
        return UNKNOWN;
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * The stable identifier within the kind: a plugin ID, an implementation class name, or a normalized script URI.
     * Empty for kinds that have a single instance.
     */
    public String getId() {
        return id;
    }

    public boolean isKnown() {
        return kind != Kind.UNKNOWN;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContributorKey)) {
            return false;
        }
        ContributorKey other = (ContributorKey) o;
        return kind == other.kind && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id);
    }

    @Override
    public String toString() {
        return id.isEmpty() ? kind.toString() : kind + "(" + id + ")";
    }
}
