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

import org.apache.ivy.core.RelativeUrlResolver;
import org.apache.ivy.core.cache.ResolutionCacheManager;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.module.status.StatusManager;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.conflict.ConflictManager;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.namespace.Namespace;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RestrictedDependencyResolver;

import java.io.File;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

/**
 * An implementation of {@link ParserSettings} that is useful for parsing an ivy.xml file without attempting to download
 * other resources from a DependencyResolver.
 */
public class DisconnectedParserSettings implements ParserSettings {
    private final IvySettings ivySettings = new IvySettings();

    /**
     * This implementation will not attempt to download any parent modules.
     * TODO:DAZ Work out how to do the actual download if we want to do more validation when publishing.
     */
    public DependencyResolver getResolver(final ModuleRevisionId mRevId) {
        return new RestrictedDependencyResolver() {
            @Override
            public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
                return new ResolvedModuleRevision(null, null, new DefaultModuleDescriptor(mRevId, "release", new Date()), null);
            }
        };
    }

    public String substitute(String value) {
        return ivySettings.substitute(value);
    }

    public PatternMatcher getMatcher(String matcherName) {
        return ivySettings.getMatcher(matcherName);
    }

    public StatusManager getStatusManager() {
        return ivySettings.getStatusManager();
    }

    public ConflictManager getConflictManager(String name) {
        return ivySettings.getConflictManager(name);
    }

    public Namespace getNamespace(String namespace) {
        return ivySettings.getNamespace(namespace);
    }

    public Namespace getContextNamespace() {
        return ivySettings.getContextNamespace();
    }

    // The reset of the methods are not used when parsing an ivy.xml
    public Map substitute(Map strings) {
        throw unsupported();
    }

    public ResolutionCacheManager getResolutionCacheManager() {
        throw unsupported();
    }

    public RelativeUrlResolver getRelativeUrlResolver() {
        throw unsupported();
    }

    public File resolveFile(String filename) {
        throw unsupported();
    }

    public String getDefaultBranch(ModuleId moduleId) {
        throw unsupported();
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }

}
