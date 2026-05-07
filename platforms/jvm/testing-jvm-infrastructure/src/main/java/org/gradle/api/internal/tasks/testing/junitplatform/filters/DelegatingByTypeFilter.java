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
 * Adding <strong>only</strong> this type to the JUnit Platform launch request
 * prevents the class-based {@link ClassMethodNameFilter} filter to be run against non-class-based test descriptors
 * and have to report "included" (and vice versa, with {@link FilePathFilter}).  That
 * filter is n/a in that situation and can't render a meaningful opinion.  Delegating
 * by type allows each filter to be applied only to the test descriptors it can
 * meaningfully filter.
 */
public final class DelegatingByTypeFilter implements PostDiscoveryFilter {
    private final Map<Class<? extends TestSource>, PostDiscoveryFilter> delegates = new HashMap<>();

    public void addDelegate(Class<? extends TestSource> source, PostDiscoveryFilter filter) {
        delegates.put(source, filter);
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        TestSource source = descriptor.getSource().orElse(null);
        if (source == null) {
            return FilterResult.included("absent source"); // No opinion on absent sources, so allow them to pass this filter
        }

        PostDiscoveryFilter filter = delegates.get(source.getClass());
        if (filter == null) {
            return FilterResult.included("unknown source"); // No opinion on sources that haven't had a delegate added, so allow them to pass this filter
        }

        return filter.apply(descriptor);
    }
}
