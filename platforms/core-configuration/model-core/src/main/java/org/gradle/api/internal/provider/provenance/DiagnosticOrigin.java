/*
 * Copyright 2026 Gradle and contributors.
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

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Origin-only diagnostic detail. Script roles are supplied by application boundaries, never inferred from filenames.
 */
public final class DiagnosticOrigin {
    public enum Kind { PLUGIN_ID, PLUGIN_CLASS, PROJECT_SCRIPT, SETTINGS_SCRIPT, APPLIED_SCRIPT, INIT_SCRIPT, PRECOMPILED_SCRIPT, UNKNOWN }

    private final Kind kind;
    private final String identifier;
    private final String displayName;

    public DiagnosticOrigin(Kind kind, String identifier, String displayName) {
        this.kind = Objects.requireNonNull(kind);
        this.identifier = Objects.requireNonNull(identifier);
        this.displayName = Objects.requireNonNull(displayName);
    }

    public Kind getKind() {
        return kind;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiagnosticOrigin)) {
            return false;
        }
        DiagnosticOrigin that = (DiagnosticOrigin) other;
        return Objects.equals(kind, that.kind)
            && Objects.equals(identifier, that.identifier)
            && Objects.equals(displayName, that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, identifier, displayName);
    }
}
