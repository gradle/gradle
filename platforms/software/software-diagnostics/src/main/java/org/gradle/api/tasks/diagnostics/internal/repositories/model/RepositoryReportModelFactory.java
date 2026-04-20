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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.UrlArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.authentication.Authentication;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.RepositoryContentDescriptorInternal;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@NullMarked
public final class RepositoryReportModelFactory {

    public ReportRepository toReportRepository(
        AbstractArtifactRepository repo,
        Set<RepositoryRole> roles,
        RepositoryDeclarationSite site
    ) {
        RepositoryType type = resolveType(repo);
        String location = resolveLocation(repo, type);
        boolean secure = resolveSecure(repo, type);
        List<String> authSchemes = resolveAuthSchemes(repo);
        boolean hasCredentials = resolveHasCredentials(repo);
        ReportContentFilter contentFilter = resolveContentFilter(repo);

        return new ReportRepository(
            repo.getName(),
            type,
            location,
            secure,
            authSchemes,
            hasCredentials,
            contentFilter,
            roles,
            site
        );
    }

    /**
     * Detects whether credentials were declared on the repository without ever reading their values.
     * Uses the internal `AuthenticationSupportedInternal.getConfiguredCredentials()` `Property`
     * and checks `isPresent()`: the property is only set when `credentials { ... }`,
     * `credentials(SomeCredentials)`, or `setConfiguredCredentials(...)` has been invoked.
     */
    private boolean resolveHasCredentials(ArtifactRepository repo) {
        if (!(repo instanceof AuthenticationSupportedInternal)) {
            return false;
        }
        return ((AuthenticationSupportedInternal) repo).getConfiguredCredentials().isPresent();
    }

    private RepositoryType resolveType(ArtifactRepository repo) {
        if (repo instanceof DefaultMavenLocalArtifactRepository) {
            return RepositoryType.MAVEN_LOCAL;
        }
        if (repo instanceof MavenArtifactRepository) {
            return RepositoryType.MAVEN;
        }
        if (repo instanceof IvyArtifactRepository) {
            return RepositoryType.IVY;
        }
        if (repo instanceof FlatDirectoryArtifactRepository) {
            return RepositoryType.FLAT_DIR;
        }
        throw new IllegalArgumentException("Unsupported repository type: " + repo.getClass().getName());
    }

    private String resolveLocation(ArtifactRepository repo, RepositoryType type) {
        switch (type) {
            case MAVEN:
            case MAVEN_LOCAL:
                return String.valueOf(((MavenArtifactRepository) repo).getUrl());
            case IVY:
                return String.valueOf(((IvyArtifactRepository) repo).getUrl());
            case FLAT_DIR:
                Set<File> dirs = ((FlatDirectoryArtifactRepository) repo).getDirs();
                String joined = dirs.stream()
                    .map(File::getAbsolutePath)
                    .sorted()
                    .collect(Collectors.joining(", "));
                return "dirs:[" + joined + "]";
            default:
                throw new IllegalStateException("Unexpected type: " + type);
        }
    }

    private boolean resolveSecure(ArtifactRepository repo, RepositoryType type) {
        if (type == RepositoryType.FLAT_DIR) {
            return true;
        }
        if (repo instanceof UrlArtifactRepository) {
            return !((UrlArtifactRepository) repo).isAllowInsecureProtocol();
        }
        return true;
    }

    private List<String> resolveAuthSchemes(ArtifactRepository repo) {
        if (!(repo instanceof AuthenticationSupported)) {
            return ImmutableList.of();
        }
        Collection<Authentication> auths = ((AuthenticationSupported) repo).getAuthentication();
        if (auths == null || auths.isEmpty()) {
            return ImmutableList.of();
        }
        List<String> names = new ArrayList<>(auths.size());
        for (Authentication a : auths) {
            names.add(a.getClass().getSimpleName());
        }
        Collections.sort(names);
        return ImmutableList.copyOf(names);
    }

    private ReportContentFilter resolveContentFilter(AbstractArtifactRepository repo) {
        RepositoryContentDescriptorInternal desc = repo.getRepositoryDescriptorCopy();
        List<String> includes = desc.describeIncludeRules();
        List<String> excludes = desc.describeExcludeRules();
        Set<String> onlyForConfigs = desc.getIncludedConfigurations() == null
            ? ImmutableSet.of() : ImmutableSet.copyOf(desc.getIncludedConfigurations());
        Set<String> notForConfigs = desc.getExcludedConfigurations() == null
            ? ImmutableSet.of() : ImmutableSet.copyOf(desc.getExcludedConfigurations());
        Map<String, Set<String>> attrs;
        Map<Attribute<Object>, Set<Object>> raw = desc.getRequiredAttributes();
        if (raw == null || raw.isEmpty()) {
            attrs = ImmutableMap.of();
        } else {
            Map<String, Set<String>> stringified = new TreeMap<>();
            for (Map.Entry<Attribute<Object>, Set<Object>> e : raw.entrySet()) {
                Set<String> values = e.getValue().stream()
                    .map(String::valueOf).collect(Collectors.toCollection(java.util.TreeSet::new));
                stringified.put(e.getKey().getName(), values);
            }
            attrs = stringified;
        }
        return new ReportContentFilter(includes, excludes, onlyForConfigs, notForConfigs, attrs);
    }
}
