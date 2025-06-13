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

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.BuildTree.class)
public interface ConfigurationCacheDegradationController {
    /**
     * Registers a Configuration Cache degradation request for a given {@code task}. Each task can have multiple reasons for degradation registered.
     * <p>
     * If the {@code task} is present in the task graph and the {@code reason} is present, then all Configuration Cache problems triggered by the task will
     * be suppressed and Configuration Cache will be disabled, switching the build to the vintage execution mode.
     * <p>
     * The reasons are evaluated immediately before the serialization of the task graph and effectively prevent serialization if they are present.
     * <p>
     * Adding a degradation is thread-safe.
     */
    void requireConfigurationCacheDegradation(Task task, Provider<String> reason);
}
