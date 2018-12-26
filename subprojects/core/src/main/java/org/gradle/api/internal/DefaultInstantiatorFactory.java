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
import org.gradle.api.internal.instantiation.InstanceFactory;
import org.gradle.api.internal.instantiation.Jsr330ConstructorSelector;
import org.gradle.api.internal.instantiation.ParamsMatchingConstructorSelector;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

import java.util.List;

public class DefaultInstantiatorFactory implements InstantiatorFactory {
    private final ConstructorSelector injectOnlyJsr330Selector;
    private final ConstructorSelector injectOnlyLenientSelector;
    private final ConstructorSelector decoratedJsr330Selector;
    private final ConstructorSelector decoratedLenientSelector;
    private final Instantiator decoratingLenientInstantiator;
    private final DependencyInjectingInstantiator injectOnlyJsr330Instantiator;
    private final Instantiator injectOnlyLenientInstantiator;

    public DefaultInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory) {
        ClassGenerator injectOnly = AsmBackedClassGenerator.injectOnly();
        ClassGenerator decorated = AsmBackedClassGenerator.decorateAndInject();
        ServiceRegistry noServices = new DefaultServiceRegistry();
        injectOnlyJsr330Selector = new Jsr330ConstructorSelector(injectOnly, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
        decoratedJsr330Selector = new Jsr330ConstructorSelector(decorated, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
        injectOnlyLenientSelector = new ParamsMatchingConstructorSelector(injectOnly, cacheFactory.<List<? extends ClassGenerator.GeneratedConstructor<?>>>newClassCache());
        decoratedLenientSelector = new ParamsMatchingConstructorSelector(decorated, cacheFactory.<List<? extends ClassGenerator.GeneratedConstructor<?>>>newClassCache());
        decoratingLenientInstantiator = new DependencyInjectingInstantiator(decoratedLenientSelector, noServices);
        injectOnlyJsr330Instantiator = new DependencyInjectingInstantiator(injectOnlyJsr330Selector, noServices);
        injectOnlyLenientInstantiator = new DependencyInjectingInstantiator(injectOnlyLenientSelector, noServices);
    }

    @Override
    public Instantiator inject(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(injectOnlyJsr330Selector, registry);
    }

    @Override
    public Instantiator inject() {
        return injectOnlyJsr330Instantiator;
    }

    @Override
    public <T> InstanceFactory<T> injectFactory(Class<T> type) {
        return injectOnlyJsr330Instantiator.factoryFor(type);
    }

    @Override
    public Instantiator injectLenient() {
        return injectOnlyLenientInstantiator;
    }

    @Override
    public Instantiator injectLenient(ServiceRegistry services) {
        return new DependencyInjectingInstantiator(injectOnlyLenientSelector, services);
    }

    @Override
    public Instantiator decorateLenient() {
        return decoratingLenientInstantiator;
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
