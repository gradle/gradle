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

package org.gradle.internal.service;

import org.gradle.internal.concurrent.Stoppable;

import java.lang.reflect.Type;

/**
 * Provides a set of zero or more services. The get-methods may be called concurrently. {@link #stop()} is guaranteed to be only called once,
 * after all get-methods have completed.
 */
interface ServiceProvider extends Stoppable {
    /**
     * Locates a service instance of the given type. Returns null if this provider does not provide a service of this type.
     */
    Service getService(Type serviceType);

    /**
     * Locates a factory for services of the given type. Returns null if this provider does not provide any services of this type.
     */
    Service getFactory(Class<?> type);

    /**
     * Collects all services of the given type.
     *
     * @return A visitor that should be used for all subsequent services.
     */
    Visitor getAll(Class<?> serviceType, Visitor visitor);

    interface Visitor {
        void visit(Service service);
    }
}
