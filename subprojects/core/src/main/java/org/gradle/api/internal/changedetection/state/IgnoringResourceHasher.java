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
import com.google.common.collect.Iterables;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.file.pattern.PathMatcher;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.file.FilePathUtil;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;

public class IgnoringResourceHasher implements ResourceHasher {
    private final ResourceHasher delegate;
    private final Set<String> ignores;
    private final ImmutableSet<PathMatcher> ignoreMatchers;

    public IgnoringResourceHasher(Set<String> ignores, ResourceHasher delegate) {
        this.delegate = delegate;
        this.ignores = ImmutableSet.copyOf(ignores);
        ImmutableSet.Builder<PathMatcher> builder = ImmutableSet.builder();
        for (String ignore : ignores) {
            PathMatcher matcher = PatternMatcherFactory.compile(true, ignore);
            builder.add(matcher);
        }
        this.ignoreMatchers = builder.build();
    }

    @Nullable
    @Override
    public HashCode hash(PhysicalFileSnapshot fileSnapshot, Iterable<String> relativePath) {
        if (shouldBeIgnored(Iterables.toArray(relativePath, String.class))) {
            return null;
        }
        return delegate.hash(fileSnapshot, relativePath);
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        if (shouldBeIgnored(FilePathUtil.getPathSegments(zipEntry.getName()))) {
            return null;
        }
        return delegate.hash(zipEntry, zipInput);
    }

    private boolean shouldBeIgnored(String[] relativePath) {
        if (ignoreMatchers.isEmpty()) {
            return false;
        }
        for (PathMatcher ignoreSpec : ignoreMatchers) {
            if (ignoreSpec.matches(relativePath, 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendConfigurationToHasher(BuildCacheHasher hasher) {
        hasher.putString(getClass().getName());
        for (String ignore : ignores) {
            hasher.putString(ignore);
        }
        delegate.appendConfigurationToHasher(hasher);
    }
}
