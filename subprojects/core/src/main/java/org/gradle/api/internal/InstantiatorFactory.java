/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;

public class InstantiatorFactory {
    private final DependencyInjectingInstantiator.ConstructorCache constructorCache = new DependencyInjectingInstantiator.ConstructorCache();
    private final ClassGenerator classGenerator;

    public InstantiatorFactory(ClassGenerator classGenerator) {
        this.classGenerator = classGenerator;
    }

    /**
     * Creates an {@link Instantiator} that can inject services into the instances it creates, but does not decorate the instances.
     *
     * @param registry The registry of services to make available to instances.
     * @return The instantiator
     */
    public Instantiator inject(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(registry, constructorCache);
    }

    /**
     * Creates an {@link Instantiator} that can inject services into the instances it creates and also decorates the instances.
     *
     * @param registry The registry of services to make available to instances.
     * @return The instantiator
     */
    public Instantiator injectAndDecorate(ServiceRegistry registry) {
        return new ClassGeneratorBackedInstantiator(classGenerator, new DependencyInjectingInstantiator(registry, constructorCache));
    }
}
