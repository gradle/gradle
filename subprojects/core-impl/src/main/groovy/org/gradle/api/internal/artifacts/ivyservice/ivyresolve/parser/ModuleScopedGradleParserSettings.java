/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.resolver.DependencyResolver;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class ModuleScopedGradleParserSettings implements GradleParserSettings {
    private final IvySettings settings;
    private final DependencyResolver currentResolver;
    private final ModuleRevisionId currentRevisionId;

    public ModuleScopedGradleParserSettings(IvySettings settings, DependencyResolver currentResolver, ModuleRevisionId currentRevisionId) {
        this.settings = settings;
        this.currentResolver = currentResolver;
        this.currentRevisionId = currentRevisionId;
    }

    public ModuleRevisionId getCurrentRevisionId() {
        return currentRevisionId;
    }

    public DependencyResolver getResolver(ModuleRevisionId mRevId) {
        if (mRevId.equals(currentRevisionId)) {
            return currentResolver;
        }
        return settings.getResolver(mRevId);
    }

    public String substitute(String value) {
        return settings.substitute(value);
    }

    public PatternMatcher getMatcher(String matcherName) {
        return settings.getMatcher(matcherName);
    }

    public String getDefaultStatus() {
        return settings.getStatusManager().getDefaultStatus();
    }

    public Namespace getNamespace(String namespace) {
        return settings.getNamespace(namespace);
    }

    public Namespace getContextNamespace() {
        return Namespace.SYSTEM_NAMESPACE;
    }
}
