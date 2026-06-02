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

package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value object representing a single repository as it appears on the report.
 *
 * <p>Captures identity-forming attributes (name, type, location, auth/content) plus the
 * role(s) the repository plays and where it was declared. See {@link #getIdentityKey()} for
 * the structural key used by the renderer to detect duplicate declarations.
 */
@NullMarked
public final class ReportRepository {
    private final String name;
    private final RepositoryType type;
    private final String location;
    private final boolean secure;
    private final List<String> authSchemes;
    private final boolean hasCredentials;
    private final ReportContentFilter contentFilter;
    private final Set<RepositoryRole> roles;
    private final RepositoryDeclarationSite declarationSite;
    private transient IdentityKey identityKey;

    public ReportRepository(
        String name,
        RepositoryType type,
        String location,
        boolean secure,
        List<String> authSchemes,
        boolean hasCredentials,
        ReportContentFilter contentFilter,
        Set<RepositoryRole> roles,
        RepositoryDeclarationSite declarationSite
    ) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.location = Objects.requireNonNull(location);
        this.secure = secure;
        this.authSchemes = ImmutableList.copyOf(authSchemes);
        this.hasCredentials = hasCredentials;
        this.contentFilter = Objects.requireNonNull(contentFilter);
        this.roles = ImmutableSet.copyOf(roles);
        this.declarationSite = Objects.requireNonNull(declarationSite);
        this.identityKey = new IdentityKey(name, type, location, secure, this.authSchemes, hasCredentials, contentFilter);
    }

    public String getName() {
        return name;
    }

    public RepositoryType getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public boolean isSecure() {
        return secure;
    }

    public List<String> getAuthSchemes() {
        return authSchemes;
    }

    public boolean hasCredentials() {
        return hasCredentials;
    }

    public ReportContentFilter getContentFilter() {
        return contentFilter;
    }

    public Set<RepositoryRole> getRoles() {
        return roles;
    }

    public RepositoryDeclarationSite getDeclarationSite() {
        return declarationSite;
    }

    /**
     * Returns the identity key used to detect duplicate repository declarations.
     */
    public IdentityKey getIdentityKey() {
        if (identityKey == null) {
            identityKey = new IdentityKey(name, type, location, secure, authSchemes, hasCredentials, contentFilter);
        }
        return identityKey;
    }

    /**
     * Structural key for identifying "identical" repositories. Excludes roles
     * and declarationSite — two entries with the same configuration but
     * declared in different places compare equal by this key.
     */
    public static final class IdentityKey {
        private final String name;
        private final RepositoryType type;
        private final String location;
        private final boolean secure;
        private final List<String> authSchemes;
        private final boolean hasCredentials;
        private final ReportContentFilter contentFilter;

        IdentityKey(String name, RepositoryType type, String location, boolean secure, List<String> authSchemes, boolean hasCredentials, ReportContentFilter contentFilter) {
            this.name = name;
            this.type = type;
            this.location = location;
            this.secure = secure;
            this.authSchemes = authSchemes;
            this.hasCredentials = hasCredentials;
            this.contentFilter = contentFilter;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdentityKey)) {
                return false;
            }
            IdentityKey that = (IdentityKey) o;
            return secure == that.secure
                && hasCredentials == that.hasCredentials
                && name.equals(that.name)
                && type == that.type
                && location.equals(that.location)
                && authSchemes.equals(that.authSchemes)
                && contentFilter.equals(that.contentFilter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, location, secure, authSchemes, hasCredentials, contentFilter);
        }
    }
}
