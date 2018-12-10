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

import org.gradle.api.internal.instantiation.ConstructorSelector;
import org.gradle.api.internal.instantiation.Jsr330ConstructorSelector;
import org.gradle.api.internal.instantiation.ParamsMatchingConstructorSelector;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.reflect.Constructor;
import java.util.List;

public class DefaultInstantiatorFactory implements InstantiatorFactory {
    private final ClassGenerator classGenerator;
    private final Instantiator decoratingInstantiator;
    private final Instantiator undecoraredInjectingInstantiator;
    private final ConstructorSelector undecoratedJsr330Selector;
    private final ConstructorSelector decoratedJsr330Selector;
    private final ConstructorSelector decoratedLenientSelector;

    public DefaultInstantiatorFactory(ClassGenerator classGenerator, CrossBuildInMemoryCacheFactory cacheFactory) {
        this.classGenerator = classGenerator;
        this.decoratingInstantiator = new ClassGeneratorBackedInstantiator(classGenerator, DirectInstantiator.INSTANCE);
        ServiceRegistry noServices = new DefaultServiceRegistry();
        ClassGenerator undecorated = new ClassGenerator() {
            @Override
            public <T> Class<? extends T> generate(Class<T> type) {
                return type;
            }
        };
        undecoratedJsr330Selector = new Jsr330ConstructorSelector(undecorated, cacheFactory.<DependencyInjectingInstantiator.CachedConstructor>newClassCache());
        decoratedJsr330Selector = new Jsr330ConstructorSelector(classGenerator, cacheFactory.<DependencyInjectingInstantiator.CachedConstructor>newClassCache());
        decoratedLenientSelector = new ParamsMatchingConstructorSelector(classGenerator, cacheFactory.<List<Constructor<?>>>newClassCache());
        undecoraredInjectingInstantiator = new DependencyInjectingInstantiator(undecoratedJsr330Selector, noServices);
    }

    @Override
    public Instantiator inject(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(undecoratedJsr330Selector, registry);
    }

    @Override
    public Instantiator inject() {
        return undecoraredInjectingInstantiator;
    }

    @Override
    public Instantiator decorate() {
        return decoratingInstantiator;
    }

    @Override
    public Instantiator injectAndDecorate(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(decoratedJsr330Selector, registry);
    }

    @Override
    public Instantiator injectAndDecorateLenient(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(decoratedLenientSelector, registry);
    }
}
