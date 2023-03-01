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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.SourceSet;

/**
 * Provides helpers to model JVM components, cross-project JVM publications
 * or resolvable configurations.
 */
@NonNullApi
@SuppressWarnings("UnusedReturnValue")
public interface JvmModelingServices {
    /**
     * Creates a generic "java component", using the specified source set and it's corresponding
     * configurations, compile tasks and jar tasks. Based on the provided {@code action}, this
     * method may also create javadocs and sources jars and configure outgoing variants
     * to be published externally.
     *
     * This method treats the {@code main} source set specially, in that it will extend the existing
     * component represented by the main source set instead of creating a new one. In practice, this
     * means that whenever the main source set is used, this method will create new configurations
     * which live adjacent to the main source set, but still compile against the main sources. However,
     * when using any other source set, the configurations of the provided source set are used.
     *
     * This can be used to create new tests, test fixtures, or any other Java component which
     * needs to live within the same project as the main component.
     *
     * @param name the name of the component to create
     * @param sourceSet The {@link SourceSet} to use as the base of this component.
     * @param action the action which configures the component to create
     */
    void createJvmVariant(String name, SourceSet sourceSet, Action<? super JvmVariantBuilder> action);
}
