/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.instantiation;

import org.gradle.internal.service.ServiceRegistry;

/**
 * Creates instances of the given type.
 */
public interface InstanceFactory<T> {
    /**
     * Is the given service required?
     */
    boolean requiresService(Class<?> serviceType);

    /**
     * Creates a new instance from the given services and parameters.
     */
    T newInstance(ServiceRegistry services, Object... params);

    /**
     * Creates a new instance from the given parameters (and no services).
     */
    T newInstance(Object... params);
}
