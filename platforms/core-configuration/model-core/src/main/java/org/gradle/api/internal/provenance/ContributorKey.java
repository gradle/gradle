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

package org.gradle.api.internal.provenance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A logical contributor in an explicitly supplied domain. Labels, applications and build paths do not define this domain.
 */
public final class ContributorKey {
    public enum Kind { PLUGIN_ID, PLUGIN_CLASS, BUILD_AUTHOR, APPLIED_SCRIPT, SETTINGS, ENVIRONMENT, UNKNOWN }

    private final String domain;
    private final Kind kind;
    private final String identity;

    public ContributorKey(String domain, Kind kind, String identity) {
        this.domain = Objects.requireNonNull(domain);
        this.kind = Objects.requireNonNull(kind);
        this.identity = Objects.requireNonNull(identity);
    }

    public String getDomain() {
        return domain;
    }

    public Kind getKind() {
        return kind;
    }

    public String getIdentity() {
        return identity;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ContributorKey)) {
            return false;
        }
        ContributorKey that = (ContributorKey) other;
        return Objects.equals(domain, that.domain)
            && Objects.equals(kind, that.kind)
            && Objects.equals(identity, that.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, kind, identity);
    }
}
