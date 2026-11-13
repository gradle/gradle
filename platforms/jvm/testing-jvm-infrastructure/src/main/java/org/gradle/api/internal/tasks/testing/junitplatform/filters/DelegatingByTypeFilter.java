/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform.filters;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * A JUnit Platform {@link PostDiscoveryFilter} that delegates filtering to other
 * {@link PostDiscoveryFilter} instances based on the type of {@link TestSource}
 * associated with the {@link TestDescriptor}.
 * <p>
 * Delegating by type allows each filter to be applied only to the test descriptors it can
 * meaningfully filter, e.g. {@link FilePathFilter} to file-based tests. Descriptors whose source
 * has no dedicated delegate, including descriptors without a source, are handed to the default delegate.
 */
public final class DelegatingByTypeFilter implements PostDiscoveryFilter {
    private final Map<Class<? extends TestSource>, PostDiscoveryFilter> delegates = new HashMap<>();
    private final PostDiscoveryFilter defaultDelegate;

    public DelegatingByTypeFilter(PostDiscoveryFilter defaultDelegate) {
        this.defaultDelegate = defaultDelegate;
    }

    public void addDelegate(Class<? extends TestSource> source, PostDiscoveryFilter filter) {
        delegates.put(source, filter);
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        PostDiscoveryFilter delegate = descriptor.getSource()
            .map(source -> delegates.get(source.getClass()))
            .orElse(defaultDelegate);
        return delegate.apply(descriptor);
    }
}
