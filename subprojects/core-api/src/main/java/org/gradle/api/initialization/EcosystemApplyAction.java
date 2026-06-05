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

package org.gradle.api.initialization;

import org.gradle.api.Incubating;

/**
 * A settings-time unit that aggregates project features (via {@code @RegistersProjectFeatures}) and
 * optionally seeds their model defaults. An ecosystem is applied by id in a settings
 * {@code plugins { }} block.
 *
 * @since 9.7.0
 */
@Incubating
public interface EcosystemApplyAction {
    /**
     * Seed model defaults for the project types and features that this ecosystem registers. Defaults to
     * a no-op so an aggregation-only ecosystem need not implement it. Called at most once, at settings
     * time, after the aggregated features have been discovered.
     *
     * @param defaults the shared model defaults to configure
     *
     * @since 9.7.0
     */
    default void apply(SharedModelDefaults defaults) {
    }
}
