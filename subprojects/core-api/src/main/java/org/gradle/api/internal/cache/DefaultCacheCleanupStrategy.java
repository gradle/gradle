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

package org.gradle.api.internal.cache;

import org.gradle.api.provider.Provider;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupFrequency;

import java.util.function.Supplier;

public class DefaultCacheCleanupStrategy implements CacheCleanupStrategy {
    private final CleanupAction cleanupAction;
    private final Supplier<CleanupFrequency> cleanupFrequency;

    private DefaultCacheCleanupStrategy(CleanupAction cleanupAction, Supplier<CleanupFrequency> cleanupFrequency) {
        this.cleanupAction = cleanupAction;
        this.cleanupFrequency = cleanupFrequency;
    }

    public static DefaultCacheCleanupStrategy from(CleanupAction cleanupAction, Provider<CleanupFrequency> cleanupFrequency) {
        return new DefaultCacheCleanupStrategy(cleanupAction, cleanupFrequency::get);
    }

    public static DefaultCacheCleanupStrategy from(CleanupAction cleanupAction) {
        return new DefaultCacheCleanupStrategy(cleanupAction, () -> CleanupFrequency.DAILY);
    }

    @Override
    public CleanupAction getCleanupAction() {
        return cleanupAction;
    }

    @Override
    public CleanupFrequency getCleanupFrequency() {
        return cleanupFrequency.get();
    }
}
