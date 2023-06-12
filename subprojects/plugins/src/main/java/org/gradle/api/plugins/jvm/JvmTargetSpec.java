/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.JavaVersion;
import org.gradle.testing.base.MatrixContainer;

public interface JvmTargetSpec extends MatrixContainer.MatrixSpec {
    /**
     * Set the required values for the {@link JavaVersionAxis}.
     *
     * @param values the values
     * @throws IllegalArgumentException if the axis is not registered
     * @throws IllegalArgumentException if the values are empty
     */
    void javaVersion(JavaVersion... values);

    /**
     * Set the required values for the {@link JavaVersionAxis}.
     *
     * @param values the values
     * @throws IllegalArgumentException if the axis is not registered
     * @throws IllegalArgumentException if the values are empty
     */
    void javaVersion(Iterable<JavaVersion> values);
}
