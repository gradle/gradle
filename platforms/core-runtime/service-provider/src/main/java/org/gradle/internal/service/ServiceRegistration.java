/*
 * Copyright 2013 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Allows services to be added to a registry.
 */
public interface ServiceRegistration {

    /**
     * Adds an implementation of one or more services to this registry.
     * <p>
     * The implementation class should have a single public constructor, and this constructor can take services to be injected as parameters.
     * <p>
     * Use {@link ServiceRegistration.Contracts#provides(Class, Class) provides} method to make declarations concise:
     *
     * <pre><code class="language-java">registration.add(MyImplementation.class, provides(ServiceOne.class, ServiceTwo.class));</code></pre>
     *
     * @param implementationType a class implementing the services
     * @param contract list of services to expose
     * @param <T> implementation type
     */
    <T> void add(Class<T> implementationType, ServiceContract<T> contract);

    /**
     * Adds a service to this registry. The given object is closed when the associated registry is closed.
     *
     * @param serviceType The type to make this service visible as.
     * @param serviceInstance The service implementation.
     */
    <T> void add(Class<T> serviceType, T serviceInstance);

    /**
     * Adds a service to this registry. The implementation class should have a single public constructor, and this constructor can take services to be injected as parameters.
     *
     * @param serviceType The service implementation to make visible.
     */
    void add(Class<?> serviceType);

    /**
     * Adds a service to this registry. The implementation class should have a single public constructor, and this constructor can take services to be injected as parameters.
     *
     * @param serviceType The service to make visible.
     * @param implementationType The implementation type of the service.
     */
    <T> void add(Class<? super T> serviceType, Class<T> implementationType);

    /**
     * Adds two services to this registry that share the implementation.
     *
     * @param serviceType1 The first service to make visible.
     * @param serviceType2 The second service to make visible.
     * @param implementationType The implementation type of the service.
     */
    <T> void add(Class<? super T> serviceType1, Class<? super T> serviceType2, Class<T> implementationType);

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods. See {@link DefaultServiceRegistry} for details.
     */
    void addProvider(ServiceRegistrationProvider provider);


    class Contracts {
        // Note: the extra class is required only because Java 6 does not allow static methods on interfaces

        /**
         * Creates a contract with one provided service
         */
        public static <T> ServiceContract<T> provides(Class<? super T> serviceType) {
            return providesServices(Collections.<Class<? super T>>singletonList(serviceType));
        }

        /**
         * Creates a contract with two provided services
         */
        @SuppressWarnings({"unchecked", "RedundantSuppression"})
        public static <T> ServiceContract<T> provides(Class<? super T> serviceType1, Class<? super T> serviceType2) {
            // Note: public methods are not varargs, because this code is Java 6 and cannot make use of @SafeVarargs
            // and all callsites would have to declare unchecked array creation
            return providesServices(Arrays.<Class<? super T>>asList(serviceType1, serviceType2));
        }

        private static <T> ServiceContract<T> providesServices(final List<Class<? super T>> serviceTypes) {
            return new ServiceContract<T>() {
                @Override
                public List<Class<? super T>> getProvidedServices() {
                    return serviceTypes;
                }
            };
        }
    }
}
