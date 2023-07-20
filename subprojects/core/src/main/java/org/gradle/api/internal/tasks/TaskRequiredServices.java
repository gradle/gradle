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

package org.gradle.api.internal.tasks;

import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.internal.BuildServiceProvider;

import java.util.List;
import java.util.Set;

/**
 * Represents the set of shared build services required by a task.
 */
public interface TaskRequiredServices {
    void registerServiceUsage(Provider<? extends BuildService<?>> serviceUsed);

    /**
     * Returns services required, be it using {@link org.gradle.api.Task#usesService} or
     * by annotating a property as {@link org.gradle.api.services.ServiceReference}.
     */
    Set<Provider<? extends BuildService<?>>> searchServices();

    Set<Provider<? extends BuildService<?>>> getElements();

    /**
     * Returns whether a service is required, be it using {@link org.gradle.api.Task#usesService} or
     * by annotating a property as {@link org.gradle.api.services.ServiceReference}.
     */
    boolean isServiceRequired(Provider<? extends BuildService<?>> toCheck);

    void acceptServiceReferences(List<? extends BuildServiceProvider<?, ?>> serviceReferences);

    boolean hasServiceReferences();
}
