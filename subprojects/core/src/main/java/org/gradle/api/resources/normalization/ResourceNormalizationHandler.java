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

package org.gradle.api.resources.normalization;

import org.gradle.api.Action;

/**
 * Used to declare normalization strategies.
 *
 * Currently, it is only possible to configure normalization strategies for the runtime classpath.
 */
public interface ResourceNormalizationHandler {
    /**
     * Returns the normalization strategy for the runtime classpath.
     */
    RuntimeClasspathNormalizationStrategy getRuntimeClasspath();

    /**
     * Configures the normalization strategy for the runtime classpath.
     */
    void runtimeClasspath(Action<? super RuntimeClasspathNormalizationStrategy> configuration);
}
