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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.matcher.Matcher;
import org.apache.ivy.plugins.matcher.NoMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.gradle.api.GradleException;

import java.lang.reflect.Method;

class ChangingModuleDetector {
    private final ResolverSettings settings;

    public ChangingModuleDetector(ResolverSettings settings) {
        this.settings = settings;
    }

    public boolean isChangingModule(DependencyResolver resolver, ResolvedModuleRevision revision, ResolveData resolveData) {
        CacheMetadataOptions options = getCacheMetadataOptions(resolver, resolveData);
        return getChangingMatcher(options).matches(revision.getId().getRevision());
    }

    private Matcher getChangingMatcher(CacheMetadataOptions options) {
        String changingMatcherName = options.getChangingMatcherName();
        String changingPattern = options.getChangingPattern();
        if (changingMatcherName == null || changingPattern == null) {
            return NoMatcher.INSTANCE;
        }
        PatternMatcher matcher = settings.getMatcher(changingMatcherName);
        if (matcher == null) {
            throw new IllegalStateException("unknown matcher '" + changingMatcherName
                    + "'. It is set as changing matcher in " + this);
        }
        return matcher.getMatcher(changingPattern);
    }

    private CacheMetadataOptions getCacheMetadataOptions(DependencyResolver resolver, ResolveData resolveData) {
        if (resolver instanceof AbstractResolver) {
            try {
                Method method = AbstractResolver.class.getDeclaredMethod("getCacheOptions", ResolveData.class);
                method.setAccessible(true);
                return (CacheMetadataOptions) method.invoke(resolver, resolveData);
            } catch (Exception e) {
                throw new GradleException("Could not get cache options from AbstractResolver", e);
            }
        }
        return new CacheMetadataOptions();
    }

}
