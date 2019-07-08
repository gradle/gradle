/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts.transform;

/**
 * Marker interface for parameter objects to {@link TransformAction}s.
 *
 * <p>
 *     Parameter types should be interfaces, only declaring getters for {@link org.gradle.api.provider.Property}-like objects.
 *     All getters must be annotated with an input annotation like {@link org.gradle.api.tasks.Input} or {@link org.gradle.api.tasks.InputFiles}.
 *     Normalization annotations such as {@link org.gradle.api.tasks.PathSensitive} or {@link org.gradle.api.tasks.Classpath} can be used as well.
 *     See the <a href="https://docs.gradle.org/current/userguide/more_about_tasks.html#table:incremental_build_annotations">table of incremental build property type annotations</a> for all annotations which can be used.
 *     Example:
 * </p>
 * <pre class='autoTested'>
 * public interface MyParameters extends TransformParameters {
 *     {@literal @}Input
 *     Property&lt;String&gt; getStringParameter();
 *     {@literal @}InputFiles
 *     ConfigurableFileCollection getInputFiles();
 * }
 * </pre>
 *
 * @since 5.3
 */
public interface TransformParameters {
    /**
     * Used for {@link TransformAction}s without parameters.
     *
     * <p>When {@link None} is used as parameters, calling {@link TransformAction#getParameters()} throws an exception.</p>
     *
     * @since 5.3
     */
    final class None implements TransformParameters {
        private None() {}
    }
}
