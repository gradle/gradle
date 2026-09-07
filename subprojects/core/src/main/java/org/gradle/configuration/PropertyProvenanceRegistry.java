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

package org.gradle.configuration;

import org.gradle.api.Describable;
import org.gradle.api.internal.provenance.Attribution;
import org.gradle.api.internal.provenance.ContributorKey;
import org.gradle.api.internal.provenance.DiagnosticOrigin;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeApplicationId;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Build-lifetime diagnostic attribution. Cache keys are application IDs and values are descriptors only,
 * so this registry does not retain applications, plugin instances, class loaders or configured properties.
 * Domains are session-local build namespaces; this does not define persistent cross-build identity.
 */
@NullMarked
@ServiceScope(Scope.Build.class)
public class PropertyProvenanceRegistry {
    private final boolean enabled;
    private final UserCodeApplicationContext context;
    private final String domain;
    @Nullable
    private final AtomicLong propertyIds;
    @Nullable
    private final Map<UserCodeApplicationId, Attribution> attributions;
    @Nullable
    private final Attribution unknown;

    public PropertyProvenanceRegistry(boolean enabled, UserCodeApplicationContext context) {
        this.enabled = enabled;
        propertyIds = enabled ? new AtomicLong() : null;
        attributions = enabled ? new ConcurrentHashMap<>() : null;
        this.context = context;
        domain = enabled ? UUID.randomUUID().toString() : "";
        unknown = enabled ? new Attribution(new ContributorKey(domain, ContributorKey.Kind.UNKNOWN, ""),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, "", "unknown code"), new ScopeIdentity("unknown", "unknown"), null) : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String newOccurrenceScope() {
        return domain + "/property/" + Objects.requireNonNull(propertyIds).incrementAndGet();
    }

    public Attribution currentAttribution() {
        if (!enabled) {
            throw new IllegalStateException("Disabled properties must not request attribution.");
        }
        UserCodeApplicationContext.Application application = context.current();
        if (application == null) {
            return Objects.requireNonNull(unknown);
        }
        Map<UserCodeApplicationId, Attribution> cache = Objects.requireNonNull(attributions);
        Attribution existing = cache.get(application.getId());
        if (existing != null) {
            return existing;
        }
        Attribution created = describe(application);
        Attribution raced = cache.putIfAbsent(application.getId(), created);
        return raced == null ? created : raced;
    }

    public UserCodeSource binarySource(Describable displayName, String className, @Nullable String pluginId, @Nullable ConfigurationTargetIdentifier target) {
        return new ScopedBinary(displayName, className, pluginId, scopeOf(target), domain);
    }

    public UserCodeSource scriptSource(Describable displayName, @Nullable URI uri, @Nullable ConfigurationTargetIdentifier target, boolean topLevel) {
        DiagnosticOrigin.Kind kind = DiagnosticOrigin.Kind.APPLIED_SCRIPT;
        if (topLevel) {
            kind = target == null ? DiagnosticOrigin.Kind.UNKNOWN : target.getTargetType() == ConfigurationTargetIdentifier.Type.PROJECT ? DiagnosticOrigin.Kind.PROJECT_SCRIPT
                : target.getTargetType() == ConfigurationTargetIdentifier.Type.SETTINGS ? DiagnosticOrigin.Kind.SETTINGS_SCRIPT : DiagnosticOrigin.Kind.INIT_SCRIPT;
        }
        return new ScopedScript(displayName, uri, scopeOf(target), domain, kind);
    }

    public static ScopeIdentity scopeOf(@Nullable ConfigurationTargetIdentifier target) {
        return target == null ? new ScopeIdentity("unknown", "unknown") : new ScopeIdentity(target.getBuildPath(),
            target.getTargetType() == ConfigurationTargetIdentifier.Type.PROJECT ? Objects.requireNonNull(target.getTargetPath()) : target.getTargetType().label);
    }

    private Attribution describe(UserCodeApplicationContext.Application application) {
        UserCodeSource source = application.getSource();
        if (!(source instanceof ScopedSource)) {
            // Unsupported propagation must not invent a contributor from the invoker's target or display text.
            return Objects.requireNonNull(unknown);
        }
        ScopedSource scoped = (ScopedSource) source;
        DiagnosticOrigin.Kind originKind;
        ContributorKey.Kind contributorKind;
        String identity;
        String contributorIdentity;
        if (source instanceof UserCodeSource.Binary) {
            UserCodeSource.Binary binary = (UserCodeSource.Binary) source;
            String pluginId = binary.getPluginId();
            identity = pluginId == null ? binary.getClassName() : pluginId;
            contributorIdentity = identity;
            originKind = pluginId == null ? DiagnosticOrigin.Kind.PLUGIN_CLASS : DiagnosticOrigin.Kind.PLUGIN_ID;
            contributorKind = pluginId == null ? ContributorKey.Kind.PLUGIN_CLASS : ContributorKey.Kind.PLUGIN_ID;
        } else {
            ScopedScript script = (ScopedScript) source;
            URI uri = script.getUri();
            identity = uri == null ? "" : uri.normalize().toASCIIString();
            originKind = script.kind;
            contributorKind = originKind == DiagnosticOrigin.Kind.PROJECT_SCRIPT ? ContributorKey.Kind.BUILD_AUTHOR
                : originKind == DiagnosticOrigin.Kind.SETTINGS_SCRIPT ? ContributorKey.Kind.SETTINGS
                : originKind == DiagnosticOrigin.Kind.INIT_SCRIPT ? ContributorKey.Kind.ENVIRONMENT
                : originKind == DiagnosticOrigin.Kind.APPLIED_SCRIPT && uri != null ? ContributorKey.Kind.APPLIED_SCRIPT : ContributorKey.Kind.UNKNOWN;
            contributorIdentity = contributorKind == ContributorKey.Kind.APPLIED_SCRIPT ? identity : "";
        }
        return new Attribution(new ContributorKey(scoped.domain(), contributorKind, contributorIdentity),
            new DiagnosticOrigin(originKind, identity, source.getDisplayName().getDisplayName()), scoped.scope(), Long.toString(application.getId().longValue()));
    }

    private interface ScopedSource {
        ScopeIdentity scope();
        String domain();
    }

    private static class ScopedBinary extends UserCodeSource.Binary implements ScopedSource {
        private final ScopeIdentity scope;
        private final String domain;

        ScopedBinary(Describable displayName, String className, @Nullable String pluginId, ScopeIdentity scope, String domain) {
            super(displayName, className, pluginId);
            this.scope = scope;
            this.domain = domain;
        }

        @Override
        public ScopeIdentity scope() {
            return scope;
        }

        @Override
        public String domain() {
            return domain;
        }
    }

    private static class ScopedScript extends UserCodeSource.Script implements ScopedSource {
        private final ScopeIdentity scope;
        private final String domain;
        private final DiagnosticOrigin.Kind kind;

        ScopedScript(Describable displayName, @Nullable URI uri, ScopeIdentity scope, String domain, DiagnosticOrigin.Kind kind) {
            super(displayName, uri);
            this.scope = scope;
            this.domain = domain;
            this.kind = kind;
        }

        @Override
        public ScopeIdentity scope() {
            return scope;
        }

        @Override
        public String domain() {
            return domain;
        }
    }
}
