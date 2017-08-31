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

import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

public class DefaultInstantiatorFactory implements InstantiatorFactory {
    private final CrossBuildInMemoryCache<Class<?>, DependencyInjectingInstantiator.CachedConstructor> decoratedConstructorCache;
    private final CrossBuildInMemoryCache<Class<?>, DependencyInjectingInstantiator.CachedConstructor> undecoratedConstructorCache;
    private final ServiceRegistry noServices = new DefaultServiceRegistry();
    private final ClassGenerator classGenerator;
    private final Instantiator decoratingInstantiator;

    public DefaultInstantiatorFactory(ClassGenerator classGenerator, CrossBuildInMemoryCacheFactory cacheFactory) {
        this.classGenerator = classGenerator;
        this.decoratedConstructorCache = cacheFactory.newClassCache();
        this.undecoratedConstructorCache = cacheFactory.newClassCache();
        this.decoratingInstantiator = new ClassGeneratorBackedInstantiator(classGenerator, DirectInstantiator.INSTANCE);
    }

    @Override
    public Instantiator inject(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(registry, undecoratedConstructorCache);
    }

    @Override
    public Instantiator inject() {
        return new DependencyInjectingInstantiator(noServices, undecoratedConstructorCache);
    }

    @Override
    public Instantiator decorate() {
        return decoratingInstantiator;
    }

    @Override
    public Instantiator injectAndDecorate(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(classGenerator, registry, decoratedConstructorCache);
    }
}
