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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;

/**
 * A registry of build services. You use this type to register service instances.
 *
 * <p>A registry is available using {@link Gradle#getSharedServices()}.</p>
 *
 * @since 6.1
 */
public interface BuildServiceRegistry {
    /**
     * Returns the set of service registrations.
     */
    NamedDomainObjectSet<BuildServiceRegistration<?, ?>> getRegistrations();

    /**
     * Registers a service, if a service with the given name is not already registered. The service is not created until required, when the returned {@link Provider} is queried.
     *
     * @param name A name to use to identify the service.
     * @param implementationType The service implementation type. Instances of the service are created as for {@link org.gradle.api.model.ObjectFactory#newInstance(Class, Object...)}.
     * @param configureAction An action to configure the registration. You can use this to provide parameters to the service instance.
     * @return A {@link Provider} that will create the service instance when queried.
     */
    <T extends BuildService<P>, P extends BuildServiceParameters> Provider<T> registerIfAbsent(String name, Class<T> implementationType, Action<? super BuildServiceSpec<P>> configureAction);
}
