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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

import java.util.List;

public class DefaultInstantiatorFactory implements InstantiatorFactory {
    private final ClassGenerator undecorated;
    private final ClassGenerator decorated;
    private final ConstructorSelector undecoratedJsr330Selector;
    private final ConstructorSelector decoratedJsr330Selector;
    private final ConstructorSelector decoratedLenientSelector;
    private final Instantiator decoratingLenientInstantiator;
    private final Instantiator undecoraredInjectingInstantiator;
    private final Instantiator undecoratedLenientInjectingInstantiator;

    public DefaultInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory) {
        decorated = AsmBackedClassGenerator.decorateAndInject();
        undecorated = AsmBackedClassGenerator.injectOnly();
        ServiceRegistry noServices = new DefaultServiceRegistry();
        undecoratedJsr330Selector = new Jsr330ConstructorSelector(undecorated, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
        decoratedJsr330Selector = new Jsr330ConstructorSelector(decorated, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
        decoratedLenientSelector = new ParamsMatchingConstructorSelector(decorated, cacheFactory.<List<ParamsMatchingConstructorSelector.CachedConstructor>>newClassCache());
        decoratingLenientInstantiator = new DependencyInjectingInstantiator(decoratedLenientSelector, decorated, noServices);
        undecoraredInjectingInstantiator = new DependencyInjectingInstantiator(undecoratedJsr330Selector, undecorated, noServices);
        undecoratedLenientInjectingInstantiator = new DependencyInjectingInstantiator(new ParamsMatchingConstructorSelector(undecorated, cacheFactory.<List<ParamsMatchingConstructorSelector.CachedConstructor>>newClassCache()), undecorated, noServices);
    }

    @Override
    public Instantiator inject(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(undecoratedJsr330Selector, undecorated, registry);
    }

    @Override
    public Instantiator inject() {
        return undecoraredInjectingInstantiator;
    }

    @Override
    public Instantiator injectLenient() {
        return undecoratedLenientInjectingInstantiator;
    }

    @Override
    public Instantiator decorate() {
        return decoratingLenientInstantiator;
    }

    @Override
    public Instantiator injectAndDecorate(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(decoratedJsr330Selector, decorated, registry);
    }

    @Override
    public Instantiator injectAndDecorateLenient(ServiceRegistry registry) {
        return new DependencyInjectingInstantiator(decoratedLenientSelector, decorated, registry);
    }

}
