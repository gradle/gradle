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

package org.gradle.tooling.model.kotlin.dsl;

import org.gradle.api.Incubating;
import org.gradle.tooling.Failure;
import org.jspecify.annotations.Nullable;

/**
 * Resilient version of {@link KotlinDslScriptsModel} that can handle missing or incomplete script models.
 *
 * @since 9.3.0
 */
@Incubating
public interface ResilientKotlinDslScriptsModel {

    /**
     * Returns the model for Kotlin DSL scripts.
     *
     * @since 9.3.0
     */
    @Incubating
    KotlinDslScriptsModel getModel();

    /**
     * Returns the failure that occurred while trying to retrieve the model.
     *
     * @since 9.3.0
     */
    @Incubating
    @Nullable
    Failure getFailure();
}
