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

package org.gradle.normalization.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.GradleException;
import org.gradle.api.internal.changedetection.state.IgnoringResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;

public class DefaultRuntimeClasspathNormalization implements RuntimeClasspathNormalizationInternal {
    private final ImmutableSet.Builder<String> ignoresBuilder = ImmutableSet.builder();
    private ResourceFilter resourceFilter;

    @Override
    public void ignore(String pattern) {
        if (resourceFilter != null) {
            throw new GradleException("Cannot configure runtime classpath normalization after execution started.");
        }
        ignoresBuilder.add(pattern);
    }

    @Override
    public ResourceFilter getResourceFilter() {
        if (resourceFilter == null) {
            ImmutableSet<String> ignores = ignoresBuilder.build();
            resourceFilter = ignores.isEmpty() ? ResourceFilter.FILTER_NOTHING : new IgnoringResourceFilter(ignores);
        }
        return resourceFilter;
    }
}
