/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Set;

public class IgnoreResourceFilter implements MetadataFilter {
    private final Set<String> ignores;
    private final ImmutableSet<Spec<RelativePath>> ignoreSpecs;

    public IgnoreResourceFilter(Set<String> ignores) {
        this.ignores = ignores;
        ImmutableSet.Builder<Spec<RelativePath>> builder = ImmutableSet.builder();
        for (String ignore : ignores) {
            Spec<RelativePath> matcher = PatternMatcherFactory.getPatternMatcher(false, true, ignore);
            builder.add(matcher);
        }
        this.ignoreSpecs = builder.build();
    }

    @Override
    public boolean shouldBeIgnored(RelativePath relativePath) {
        if (ignoreSpecs.isEmpty()) {
            return false;
        }
        for (Spec<RelativePath> ignoreSpec : ignoreSpecs) {
            if (ignoreSpec.isSatisfiedBy(relativePath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendImplementationToHasher(BuildCacheHasher hasher) {
        hasher.putString(getClass().getName());
        for (String ignore : ignores) {
            hasher.putString(ignore);
        }
    }
}
