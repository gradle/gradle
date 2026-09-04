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

import org.jspecify.annotations.Nullable;

/**
 * The diagnostic origin of one successful property binding or one currently failing operation.
 */
public final class PropertyProvenanceRecord {
    private final String originDisplayName;
    private final PropertyProvenanceKind kind;
    private final @Nullable String location;

    public PropertyProvenanceRecord(String originDisplayName, PropertyProvenanceKind kind, @Nullable String location) {
        this.originDisplayName = originDisplayName;
        this.kind = kind;
        this.location = location;
    }

    public String getOriginDisplayName() {
        return originDisplayName;
    }

    public PropertyProvenanceKind getKind() {
        return kind;
    }

    public @Nullable String getLocation() {
        return location;
    }

    public String formatFrame() {
        String locatedOrigin = location == null
            ? originDisplayName
            : originDisplayName + " (" + location + ")";
        return "at " + locatedOrigin + " [" + kind.getDisplayName() + "]";
    }
}
