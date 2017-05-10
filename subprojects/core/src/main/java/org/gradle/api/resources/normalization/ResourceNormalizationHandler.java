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
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Used to configure resource normalizations.
 *
 * Currently, it is only possible to configure resource normalizations for the runtime classpath.
 *
 * @since 4.0
 */
@Incubating
@HasInternalProtocol
public interface ResourceNormalizationHandler {
    /**
     * Returns the normalization strategy for the runtime classpath.
     */
    RuntimeClasspathNormalization getRuntimeClasspath();

    /**
     * Configures the normalization strategy for the runtime classpath.
     */
    void runtimeClasspath(Action<? super RuntimeClasspathNormalization> configuration);
}
