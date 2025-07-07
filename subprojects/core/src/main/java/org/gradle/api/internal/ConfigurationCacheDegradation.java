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

package org.gradle.api.internal;

import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;

/**
 * A helper class for tasks and plugins that may require configuration cache graceful degradation.
 */
@SuppressWarnings("deprecation")
public class ConfigurationCacheDegradation {
    /**
     * Contributes a conditional degradation request.
     *
     * @param task the task that needs degrading
     * @param reason a provider that produces a reason string if degradation is indeed required, or has no value otherwise
     */
    public static <T extends AbstractTask> void requireDegradation(T task, Provider<String> reason) {
        task.getServices().get(ConfigurationCacheDegradationController.class)
            .requireConfigurationCacheDegradation(task, reason);
    }

    /**
     * Contributes an unconditional degradation request.
     *
     * @param task the task that needs degrading
     * @param reason the (fixed) reason
     */
    public static <T extends AbstractTask> void requireDegradation(T task, String reason) {
        requireDegradation(task, Providers.of(reason));
    }

}
