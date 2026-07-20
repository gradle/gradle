/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.flow.services

import org.gradle.api.configuration.ConfigurationCacheOutcome
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Provides the outcome of configuration caching for the current build invocation.
 *
 * Registered by the configuration cache infrastructure only when the configuration cache is
 * enabled; when absent, the outcome is [ConfigurationCacheOutcome.NOT_ENABLED].
 *
 * Must only be queried once the scheduled work of the build has completed.
 */
@ServiceScope(Scope.BuildTree::class)
interface ConfigurationCacheOutcomeSource {
    fun outcome(): ConfigurationCacheOutcome
}
