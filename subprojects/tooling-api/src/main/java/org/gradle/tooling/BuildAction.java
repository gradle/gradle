/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling;

import org.gradle.api.Incubating;

import java.io.Serializable;

/**
 * An action that executes against a Gradle build and produces a result of type {@code T}.
 *
 * <p>You can execute a {@code BuildAction} using the {@link ProjectConnection#action(BuildAction)} method.</p>
 *
 * @param <T> The type of result produced by this action.
 * @since 1.8
 */
@Incubating
public interface BuildAction<T> extends Serializable {
    /**
     * Executes this action and returns the result.
     *
     * @param controller The controller to use to access and control the build.
     * @return The result
     * @since 1.8
     */
    T execute(BuildController controller);
}
