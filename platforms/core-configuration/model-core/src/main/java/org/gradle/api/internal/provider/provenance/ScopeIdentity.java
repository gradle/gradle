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
 * A descriptor supplied by the owning context; this does not define cross-build identity or relocation encoding.
 */
public final class ScopeIdentity {
    private final String buildIdentity;
    private final String scopePath;

    public ScopeIdentity(String buildIdentity, String scopePath) {
        this.buildIdentity = Objects.requireNonNull(buildIdentity);
        this.scopePath = Objects.requireNonNull(scopePath);
    }

    public String getBuildIdentity() {
        return buildIdentity;
    }

    public String getScopePath() {
        return scopePath;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScopeIdentity)) {
            return false;
        }
        ScopeIdentity that = (ScopeIdentity) other;
        return Objects.equals(buildIdentity, that.buildIdentity)
            && Objects.equals(scopePath, that.scopePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buildIdentity, scopePath);
    }
}
