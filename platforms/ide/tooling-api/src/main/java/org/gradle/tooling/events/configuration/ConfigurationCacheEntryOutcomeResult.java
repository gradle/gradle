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

package org.gradle.tooling.events.configuration;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.OperationResult;
import org.jspecify.annotations.NullMarked;

/**
 * The final outcome of configuration caching for a build invocation.
 * <p>
 * The concrete outcome is expressed by the subtype of this interface. More subtypes may be
 * added in future Gradle versions.
 *
 * @see ConfigurationCacheEntryStoredResult
 * @see ConfigurationCacheEntryReusedResult
 * @see ConfigurationCacheEntryStoreFailedResult
 * @see ConfigurationCacheEntryStoreSkippedResult
 * @see ConfigurationCacheEntryUndeterminedResult
 * @since 9.8.0
 */
@Incubating
@NullMarked
public interface ConfigurationCacheEntryOutcomeResult extends OperationResult {
    /**
     * Returns the number of configuration cache problems reported to the console for this build invocation.
     *
     * @return the problem count
     * @since 9.8.0
     */
    int getProblemCount();
}
