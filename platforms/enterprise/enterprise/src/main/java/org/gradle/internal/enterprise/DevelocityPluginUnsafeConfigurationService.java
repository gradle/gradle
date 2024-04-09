/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.enterprise;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.function.Supplier;

/**
 * Service to allow the Develocity plugin to do things considered "Unsafe" during configuration.
 * <p>
 * This is currently especially for ignoring configuration inputs that are handled differently in the
 * Develocity plugin.
 */
@ServiceScope(Scope.BuildTree.class)
public interface DevelocityPluginUnsafeConfigurationService {
    /**
     * Run some code without tracking its inputs.
     * <p>
     * All code that runs during {@code supplier.get()} is exempt from configuration input tracking.
     * That code can for example read files from disk or system properties at configuration time without
     * them becoming a configuration input.
     */
    <T> T withConfigurationInputTrackingDisabled(Supplier<T> supplier);
}
