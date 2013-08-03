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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RestrictedDependencyResolver;

import java.text.ParseException;
import java.util.Date;

/**
 * An implementation of {@link GradleParserSettings} that is useful for parsing an ivy.xml file without attempting to download
 * other resources from a DependencyResolver.
 */
public class DisconnectedParserSettings implements GradleParserSettings {
    private final IvySettings ivySettings = new IvySettings();

    public ModuleRevisionId getCurrentRevisionId() {
        throw new UnsupportedOperationException();
    }

    public String getDefaultStatus() {
        return ivySettings.getStatusManager().getDefaultStatus();
    }

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

}
