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

package org.gradle.internal.instantiation;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

class DefaultInstantiationScheme implements InstantiationScheme {
    private final DependencyInjectingInstantiator instantiator;
    private final ConstructorSelector constructorSelector;

    public DefaultInstantiationScheme(ConstructorSelector constructorSelector, ServiceRegistry defaultServices) {
        this.constructorSelector = constructorSelector;
        this.instantiator = new DependencyInjectingInstantiator(constructorSelector, defaultServices);
    }

    @Override
    public <T> InstanceFactory<T> forType(Class<T> type) {
        return instantiator.factoryFor(type);
    }

    @Override
    public Instantiator withServices(ServiceLookup services) {
        return new DependencyInjectingInstantiator(constructorSelector, services);
    }

    @Override
    public Instantiator instantiator() {
        return instantiator;
    }
}
