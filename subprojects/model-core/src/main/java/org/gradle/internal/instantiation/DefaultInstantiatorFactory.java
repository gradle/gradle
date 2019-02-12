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
    private final CrossBuildInMemoryCacheFactory cacheFactory;
    private final List<InjectAnnotationHandler> annotationHandlers;
    private final DefaultInstantiationScheme injectOnlyScheme;
    private final DefaultInstantiationScheme injectOnlyLenientScheme;
    private final DefaultInstantiationScheme decoratingScheme;
    private final DefaultInstantiationScheme decoratingLenientScheme;

    // Assume for now that the annotations are all part of Gradle core and are never unloaded, so use strong references to the annotation types
    private final LoadingCache<Set<Class<? extends Annotation>>, InstantiationScheme> schemes = CacheBuilder.newBuilder().build(new CacheLoader<Set<Class<? extends Annotation>>, InstantiationScheme>() {
        @Override
        public InstantiationScheme load(Set<Class<? extends Annotation>> annotations) {
            for (Class<? extends Annotation> annotation : annotations) {
                assertKnownAnnotation(annotation);
            }
            ClassGenerator classGenerator = AsmBackedClassGenerator.injectOnly(annotationHandlers, annotations);
            Jsr330ConstructorSelector constructorSelector = new Jsr330ConstructorSelector(classGenerator, cacheFactory.newClassCache());
            return new DefaultInstantiationScheme(constructorSelector, noServices);
        }
    });

    public DefaultInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory, List<InjectAnnotationHandler> annotationHandlers) {
        this.cacheFactory = cacheFactory;
        this.annotationHandlers = annotationHandlers;
        ClassGenerator injectOnly = AsmBackedClassGenerator.injectOnly(annotationHandlers, ImmutableSet.of());
        ClassGenerator decorated = AsmBackedClassGenerator.decorateAndInject(annotationHandlers, ImmutableSet.of());
        ConstructorSelector injectOnlyJsr330Selector = new Jsr330ConstructorSelector(injectOnly, cacheFactory.newClassCache());
        ConstructorSelector decoratedJsr330Selector = new Jsr330ConstructorSelector(decorated, cacheFactory.newClassCache());
        ConstructorSelector injectOnlyLenientSelector = new ParamsMatchingConstructorSelector(injectOnly, cacheFactory.newClassCache());
        ConstructorSelector decoratedLenientSelector = new ParamsMatchingConstructorSelector(decorated, cacheFactory.newClassCache());
        injectOnlyScheme = new DefaultInstantiationScheme(injectOnlyJsr330Selector, noServices);
        injectOnlyLenientScheme = new DefaultInstantiationScheme(injectOnlyLenientSelector, noServices);
        decoratingScheme = new DefaultInstantiationScheme(decoratedJsr330Selector, noServices);
        decoratingLenientScheme = new DefaultInstantiationScheme(decoratedLenientSelector, noServices);
        schemes.put(ImmutableSet.of(), injectOnlyScheme);
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
    public InstantiationScheme injectScheme() {
        return injectOnlyScheme;
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
        return injectOnlyLenientScheme.instantiator();
    }

    @Override
    public Instantiator injectLenient(ServiceLookup services) {
        return injectOnlyLenientScheme.withServices(services);
    }

    @Override
    public Instantiator decorateLenient() {
        return decoratingLenientScheme.instantiator();
    }

    @Override
    public Instantiator injectAndDecorateLenient(ServiceLookup services) {
        return decoratingLenientScheme.withServices(services);
    }

    @Override
    public InstantiationScheme decorateScheme() {
        return decoratingScheme;
    }

    @Override
    public Instantiator injectAndDecorate(ServiceLookup services) {
        return decoratingScheme.withServices(services);
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
