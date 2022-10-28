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

package org.gradle.internal.buildoption;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A service that determines whether a feature flag is enabled or not.
 */
@ServiceScope(Scopes.BuildTree.class)
public interface FeatureFlags {
    boolean isEnabled(FeatureFlag flag);

    /**
     * Explicitly enable the given flag. The resulting flag status may still be overridden.
     */
    void enable(FeatureFlag flag);

    /**
     * Checks if the given flag was enabled with {@link #enable(FeatureFlag)}. This method doesn't take overrides into account.
     * @param flag the flag to check
     * @return {@code true} if the flag was enabled
     */
    boolean isEnabledWithApi(FeatureFlag flag);
}
