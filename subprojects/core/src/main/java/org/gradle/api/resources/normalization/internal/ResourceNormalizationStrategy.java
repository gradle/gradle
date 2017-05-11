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

package org.gradle.api.resources.normalization.internal;

import java.util.Collections;

/**
 * The resource normalization strategy.
 */
public class ResourceNormalizationStrategy {
    public static final ResourceNormalizationStrategy NOT_CONFIGURED = new ResourceNormalizationStrategy(
        new RuntimeClasspathNormalizationStrategy(Collections.<String>emptySet())
    );
    private final RuntimeClasspathNormalizationStrategy runtimeClasspathNormalizationStrategy;

    public ResourceNormalizationStrategy(RuntimeClasspathNormalizationStrategy runtimeClasspathNormalizationStrategy) {
        this.runtimeClasspathNormalizationStrategy = runtimeClasspathNormalizationStrategy;
    }

    public RuntimeClasspathNormalizationStrategy getRuntimeClasspathNormalizationStrategy() {
        return runtimeClasspathNormalizationStrategy;
    }
}
