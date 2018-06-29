/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical.collection;

import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;

import java.util.Collection;
import java.util.Map;

public enum FingerprintCompareStrategy {
    ABSOLUTE(new AbsolutePathFingerprintCompareStrategy()),
    NORMALIZED(new NormalizedPathFingerprintCompareStrategy()),
    IGNORED_PATH(new IgnoredPathCompareStrategy()),
    CLASSPATH(new ClasspathCompareStrategy());

    private final Impl delegate;

    FingerprintCompareStrategy(FingerprintCompareStrategy.Impl compareStrategy) {
        this.delegate = compareStrategy;
    }

    interface Impl {
        boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded);
        void appendToHasher(BuildCacheHasher hasher, Collection<NormalizedFileSnapshot> snapshots);
    }

    public boolean visitChangesSince(TaskStateChangeVisitor visitor, Map<String, NormalizedFileSnapshot> current, Map<String, NormalizedFileSnapshot> previous, String propertyTitle, boolean includeAdded) {
        return delegate.visitChangesSince(visitor, current, previous, propertyTitle, includeAdded);
    }
    public void appendToHasher(BuildCacheHasher hasher, Map<String, NormalizedFileSnapshot> snapshots) {
        delegate.appendToHasher(hasher, snapshots.values());
    }
}
