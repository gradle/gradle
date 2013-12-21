/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.legacy;

import org.apache.ivy.core.RelativeUrlResolver;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.module.status.StatusManager;
import org.apache.ivy.plugins.conflict.ConflictManager;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.io.File;
import java.util.Map;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class LegacyResolverParserSettings implements ParserSettings {
    private final ParserSettings settings;
    private final DependencyResolver currentResolver;
    private final ModuleRevisionId currentRevisionId;

    public LegacyResolverParserSettings(ParserSettings settings, DependencyResolver currentResolver, ModuleRevisionId currentRevisionId) {
        this.settings = settings;
        this.currentResolver = currentResolver;
        this.currentRevisionId = currentRevisionId;
    }

    public DependencyResolver getResolver(ModuleRevisionId mRevId) {
        if (mRevId.equals(currentRevisionId)) {
            return currentResolver;
        }
        return settings.getResolver(mRevId);
    }

    public ConflictManager getConflictManager(String name) {
        return settings.getConflictManager(name);
    }

    public String substitute(String value) {
        return settings.substitute(value);
    }

    public Map substitute(Map strings) {
        return settings.substitute(strings);
    }

    public ResolutionCacheManager getResolutionCacheManager() {
        return settings.getResolutionCacheManager();
    }

    public PatternMatcher getMatcher(String matcherName) {
        return settings.getMatcher(matcherName);
    }

    public Namespace getNamespace(String namespace) {
        return settings.getNamespace(namespace);
    }

    public StatusManager getStatusManager() {
        return settings.getStatusManager();
    }

    public RelativeUrlResolver getRelativeUrlResolver() {
        return settings.getRelativeUrlResolver();
    }

    public File resolveFile(String filename) {
        return settings.resolveFile(filename);
    }

    public String getDefaultBranch(ModuleId moduleId) {
        return settings.getDefaultBranch(moduleId);
    }

    public Namespace getContextNamespace() {
        return settings.getContextNamespace();
    }
}
