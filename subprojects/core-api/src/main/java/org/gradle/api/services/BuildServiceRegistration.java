/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.Named;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * Details of a build service.
 *
 * @param <T> the service type.
 * @param <P> the service parameters type.
 * @since 6.1
 */
public interface BuildServiceRegistration<T extends BuildService<P>, P extends BuildServiceParameters> extends Named {
    /**
     * Returns the parameters that will be used to instantiate the service with.
     */
    P getParameters();

    /**
     * Specifies the maximum number of tasks that can use this service in parallel. Setting this to 1 means that the service will be used by a single task at a time.
     * When this property has no value defined, then any number of tasks may use this service in parallel. This is the default.
     *
     * <p>
     * IMPORTANT: the build service must be consumed via a {@link ServiceReference} property, or explicitly registered with every using task
     * via {@link org.gradle.api.Task#usesService(Provider) Task#usesService} for this constraint to be honored.
     *
     * @see ServiceReference
     * @see org.gradle.api.Task#usesService(Provider)
     */
    Property<Integer> getMaxParallelUsages();

    /**
     * Returns a {@link Provider} that will create the service instance when its value is queried.
     */
    Provider<T> getService();
}
