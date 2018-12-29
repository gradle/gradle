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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DefaultInstantiatorFactory implements InstantiatorFactory {
    private final ServiceRegistry noServices = new DefaultServiceRegistry();
    private final ConstructorSelector injectOnlyLenientSelector;
    private final ConstructorSelector decoratedJsr330Selector;
    private final ConstructorSelector decoratedLenientSelector;
    private final Instantiator decoratingLenientInstantiator;
    private final Instantiator injectOnlyLenientInstantiator;
    private final CrossBuildInMemoryCacheFactory cacheFactory;
    private final List<InjectAnnotationHandler> annotationHandlers;
    private final DefaultInstantiationScheme injectOnlyScheme;
    // Assume for now that the annotations are all part of Gradle core and are never unloaded, so use strong references to the annotation types
    private final LoadingCache<Set<Class<? extends Annotation>>, InstantiationScheme> schemes = CacheBuilder.newBuilder().build(new CacheLoader<Set<Class<? extends Annotation>>, InstantiationScheme>() {
        @Override
        public InstantiationScheme load(Set<Class<? extends Annotation>> annotations) {
            for (Class<? extends Annotation> annotation : annotations) {
                assertKnownAnnotation(annotation);
            }
            ClassGenerator classGenerator = AsmBackedClassGenerator.injectOnly(annotationHandlers, annotations);
            Jsr330ConstructorSelector constructorSelector = new Jsr330ConstructorSelector(classGenerator, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
            return new DefaultInstantiationScheme(constructorSelector, noServices);
        }
    });

    public DefaultInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory, List<InjectAnnotationHandler> annotationHandlers) {
        this.cacheFactory = cacheFactory;
        this.annotationHandlers = annotationHandlers;
        ClassGenerator injectOnly = AsmBackedClassGenerator.injectOnly(annotationHandlers, ImmutableSet.<Class<? extends Annotation>>of());
        ClassGenerator decorated = AsmBackedClassGenerator.decorateAndInject(annotationHandlers, ImmutableSet.<Class<? extends Annotation>>of());
        ConstructorSelector injectOnlyJsr330Selector = new Jsr330ConstructorSelector(injectOnly, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
        decoratedJsr330Selector = new Jsr330ConstructorSelector(decorated, cacheFactory.<Jsr330ConstructorSelector.CachedConstructor>newClassCache());
        injectOnlyLenientSelector = new ParamsMatchingConstructorSelector(injectOnly, cacheFactory.<ClassGenerator.GeneratedClass<?>>newClassCache());
        decoratedLenientSelector = new ParamsMatchingConstructorSelector(decorated, cacheFactory.<ClassGenerator.GeneratedClass<?>>newClassCache());
        decoratingLenientInstantiator = new DependencyInjectingInstantiator(decoratedLenientSelector, noServices);
        injectOnlyLenientInstantiator = new DependencyInjectingInstantiator(injectOnlyLenientSelector, noServices);
        injectOnlyScheme = new DefaultInstantiationScheme(injectOnlyJsr330Selector, noServices);
        schemes.put(ImmutableSet.<Class<? extends Annotation>>of(), injectOnlyScheme);
    }

    @Override
    public Instantiator inject(ServiceLookup services) {
        return injectOnlyScheme.withServices(services);
    }

    @Override
    public Instantiator inject() {
        return injectOnlyScheme.instantiator();
    }

    @Override
    public InstantiationScheme injectScheme(Collection<Class<? extends Annotation>> injectAnnotations) {
        try {
            return schemes.getUnchecked(ImmutableSet.copyOf(injectAnnotations));
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    @Override
    public Instantiator injectLenient() {
        return injectOnlyLenientInstantiator;
    }

    @Override
    public Instantiator injectLenient(ServiceLookup services) {
        return new DependencyInjectingInstantiator(injectOnlyLenientSelector, services);
    }

    @Override
    public Instantiator decorateLenient() {
        return decoratingLenientInstantiator;
    }

    @Override
    public Instantiator injectAndDecorate(ServiceLookup services) {
        return new DependencyInjectingInstantiator(decoratedJsr330Selector, services);
    }

    @Override
    public Instantiator injectAndDecorateLenient(ServiceLookup services) {
        return new DependencyInjectingInstantiator(decoratedLenientSelector, services);
    }

    private void assertKnownAnnotation(Class<? extends Annotation> annotation) {
        for (InjectAnnotationHandler annotationHandler : annotationHandlers) {
            if (annotationHandler.getAnnotation().equals(annotation)) {
                return;
            }
        }
        throw new IllegalArgumentException(String.format("Annotation @%s is not a registered injection annotation.", annotation.getSimpleName()));
    }
}
