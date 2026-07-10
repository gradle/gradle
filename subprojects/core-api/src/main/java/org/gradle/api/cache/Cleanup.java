/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.cache;

import org.gradle.api.Incubating;
import org.gradle.api.internal.cache.DefaultCleanup;
import org.gradle.cache.CleanupFrequency;
import org.gradle.internal.HasInternalProtocol;

/**
 * Configures cache cleanup settings that apply to all caches.
 *
 * @since 8.0
 */
@HasInternalProtocol
@Incubating
public interface Cleanup {
    /**
     * Perform cache cleanup after every build session.
     */
    Cleanup ALWAYS = new DefaultCleanup(CleanupFrequency.ALWAYS);

    /**
     * Perform cache cleanup periodically (default is only once every 24 hours).
     */
    Cleanup DEFAULT = new DefaultCleanup(CleanupFrequency.DAILY);

    /**
     * Never perform cache cleanup.
     */
    Cleanup DISABLED = new DefaultCleanup(CleanupFrequency.NEVER);
}
