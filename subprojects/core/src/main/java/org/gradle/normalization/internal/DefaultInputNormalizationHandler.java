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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.normalization.RuntimeClasspathNormalization;

public class DefaultInputNormalizationHandler implements InputNormalizationHandlerInternal {
    private final RuntimeClasspathNormalizationInternal runtimeClasspathNormalizationStrategy;
    private InputNormalizationStrategy finalStrategy;

    public DefaultInputNormalizationHandler(RuntimeClasspathNormalizationInternal runtimeClasspathNormalizationStrategy) {
        this.runtimeClasspathNormalizationStrategy = runtimeClasspathNormalizationStrategy;
    }

    @Override
    public RuntimeClasspathNormalization getRuntimeClasspath() {
        return runtimeClasspathNormalizationStrategy;
    }

    @Override
    public void runtimeClasspath(Action<? super RuntimeClasspathNormalization> configuration) {
        if (finalStrategy != null) {
            throw new GradleException("Cannot configure input normalization after execution started.");
        }
        configuration.execute(getRuntimeClasspath());
    }

    @Override
    public InputNormalizationStrategy buildFinalStrategy() {
        if (finalStrategy == null) {
            finalStrategy = new InputNormalizationStrategy(runtimeClasspathNormalizationStrategy.buildFinalStrategy());
        }
        return finalStrategy;
    }
}
