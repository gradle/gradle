/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.internal.file.pattern.PathMatcher;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.internal.hash.Hasher;

import java.util.Set;
import java.util.function.Supplier;

public class IgnoringResourceFilter implements ResourceFilter {
    private final Set<String> ignores;
    private final ImmutableSet<PathMatcher> ignoreMatchers;

    public IgnoringResourceFilter(ImmutableSet<String> ignores) {
        this.ignores = ignores;
        ImmutableSet.Builder<PathMatcher> builder = ImmutableSet.builder();
        for (String ignore : ignores) {
            PathMatcher matcher = PatternMatcherFactory.compile(true, ignore);
            builder.add(matcher);
        }
        this.ignoreMatchers = builder.build();
    }

    private boolean shouldBeIgnored(String[] relativePath) {
        for (PathMatcher ignoreSpec : ignoreMatchers) {
            if (ignoreSpec.matches(relativePath, 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        for (String ignore : ignores) {
            hasher.putString(ignore);
        }
    }

    @Override
    public boolean shouldBeIgnored(Supplier<String[]> relativePathFactory) {
        if (ignoreMatchers.isEmpty()) {
            return false;
        }
        return shouldBeIgnored(relativePathFactory.get());
    }
}
