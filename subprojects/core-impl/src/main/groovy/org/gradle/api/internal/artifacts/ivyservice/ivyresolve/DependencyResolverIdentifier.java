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

import org.apache.ivy.plugins.resolver.AbstractPatternsBasedResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.util.CollectionUtils;
import org.gradle.internal.hash.HashUtil;

import java.util.ArrayList;
import java.util.List;

public class DependencyResolverIdentifier {
    public static String forIvyResolver(DependencyResolver resolver) {
        List<String> parts = new ArrayList<String>();
        parts.add(resolver.getClass().getName());
        if (resolver instanceof AbstractPatternsBasedResolver) {
            AbstractPatternsBasedResolver patternsBasedResolver = (AbstractPatternsBasedResolver) resolver;
            parts.add(joinPatterns(patternsBasedResolver.getIvyPatterns()));
            parts.add(joinPatterns(patternsBasedResolver.getArtifactPatterns()));
            if (patternsBasedResolver.isM2compatible()) {
                parts.add("m2compatible");
            }
        } else {
            parts.add(resolver.getName());
            // TODO We should not be assuming equality between resolvers here based on name...
        }

        return calculateId(parts);
    }

    // TODO: Move this logic into ExternalResourceResolver, and add some transport-specific information (bumping the cache version)
    public static String forExternalResourceResolver(ExternalResourceResolver resolver) {
        List<String> parts = new ArrayList<String>();
        parts.add(resolver.getClass().getName());
        parts.add(joinPatterns(resolver.getIvyPatterns()));
        parts.add(joinPatterns(resolver.getArtifactPatterns()));
        if (resolver.isM2compatible()) {
            parts.add("m2compatible");
        }
        return calculateId(parts);
    }

    private static String joinPatterns(List<String> patterns) {
        return CollectionUtils.join(",", patterns);
    }

    private static String calculateId(List<String> parts) {
        String idString = CollectionUtils.join("::", parts);
        return HashUtil.createHash(idString, "MD5").asHexString();
    }
}
