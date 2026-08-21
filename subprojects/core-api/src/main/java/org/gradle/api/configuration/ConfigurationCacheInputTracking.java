/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.configuration;

import org.gradle.api.Incubating;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.concurrent.Callable;

/**
 * Controls the automatic tracking of Configuration Cache inputs.
 * <p>
 * An instance of this type can be injected into a plugin or other object by annotating a public constructor or
 * property getter method with {@code javax.inject.Inject}.
 * <p>
 * This service is intended for narrow infrastructure code that reads volatile runtime state which does not affect
 * build configuration or the work Gradle executes. For example, a custom build cache implementation can use it when
 * creating and refreshing a credentials provider that belongs to the non-serialized build cache service.
 *
 * @since 9.8.0
 */
@Incubating
@ServiceScope(Scope.BuildTree.class)
public interface ConfigurationCacheInputTracking {

    /**
     * Runs an action without recording the inputs that it reads as Configuration Cache inputs.
     * <p>
     * The scope applies to the current thread only and can be nested. Input tracking is restored when the action
     * completes or throws an exception.
     * <p>
     * For example, calling {@link System#getenv(String)} from the action does not make the environment variable a
     * Configuration Cache input. Obtaining a {@link org.gradle.api.provider.Provider Provider} backed by a
     * {@link org.gradle.api.provider.ValueSource ValueSource}, such as one returned by
     * {@link org.gradle.api.provider.ProviderFactory#environmentVariable(String)}, still records its value as a
     * Configuration Cache input. The returned value is also subject to the usual Configuration Cache serialization
     * rules if it is retained in serialized build state.
     * <p>
     * This method is unsafe. If an input read by the action affects build configuration or the work Gradle executes,
     * suppressing it can cause Gradle to reuse an incorrect Configuration Cache entry.
     *
     * @param action the action to run without automatic Configuration Cache input tracking
     * @return the value returned by the action
     * @since 9.8.0
     */
    <T> T withInputTrackingDisabledUnsafe(Callable<? extends T> action);
}
