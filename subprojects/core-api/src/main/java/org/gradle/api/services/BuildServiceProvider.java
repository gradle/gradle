/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.services;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * Provides access to a build service at execution time.
 *
 * @param <T> the service type.
 * @param <P> the service parameters type.
 *
 * @since 7.2
 */
@Incubating
public interface BuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends Provider<T> {
    /**
     * Returns a new build service provider that will require the given number of parallel usages of this service.
     * This can be used to distinguish between tasks that make heavy vs. light use of a service.
     *
     * @param parallelUsages the number of parallel usages to request
     * @see BuildServiceRegistration#getMaxParallelUsages()
     */
    BuildServiceProvider<T, P> withParallelUsages(int parallelUsages);
}
