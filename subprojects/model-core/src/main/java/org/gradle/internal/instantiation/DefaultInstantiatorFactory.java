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

import com.google.common.collect.ImmutableSet;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.state.ManagedFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class DefaultInstantiatorFactory implements InstantiatorFactory {
    private static final int MANAGED_FACTORY_ID = Objects.hashCode(ClassGeneratorBackedManagedFactory.class.getName());

    private final ServiceRegistry defaultServices;
    private final CrossBuildInMemoryCacheFactory cacheFactory;
    private final List<InjectAnnotationHandler> annotationHandlers;
    private final DefaultInstantiationScheme injectOnlyScheme;
    private final DefaultInstantiationScheme injectOnlyLenientScheme;
    private final DefaultInstantiationScheme decoratingScheme;
    private final DefaultInstantiationScheme decoratingLenientScheme;
    private final ManagedFactory managedFactory;

    public DefaultInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory, List<InjectAnnotationHandler> annotationHandlers) {
        this.cacheFactory = cacheFactory;
        this.annotationHandlers = annotationHandlers;
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        services.add(InstantiatorFactory.class, this);
        this.defaultServices = services;
        ClassGenerator injectOnly = AsmBackedClassGenerator.injectOnly(annotationHandlers, ImmutableSet.of(), cacheFactory, MANAGED_FACTORY_ID);
        ClassGenerator decorated = AsmBackedClassGenerator.decorateAndInject(annotationHandlers, ImmutableSet.of(), cacheFactory, MANAGED_FACTORY_ID);
        this.managedFactory = new ClassGeneratorBackedManagedFactory(injectOnly);
        ConstructorSelector injectOnlyJsr330Selector = new Jsr330ConstructorSelector(injectOnly, cacheFactory.newClassCache());
        ConstructorSelector decoratedJsr330Selector = new Jsr330ConstructorSelector(decorated, cacheFactory.newClassCache());
        ConstructorSelector injectOnlyLenientSelector = new ParamsMatchingConstructorSelector(injectOnly, cacheFactory.newClassCache());
        ConstructorSelector decoratedLenientSelector = new ParamsMatchingConstructorSelector(decorated, cacheFactory.newClassCache());
        injectOnlyScheme = new DefaultInstantiationScheme(injectOnlyJsr330Selector, defaultServices, ImmutableSet.of(Inject.class), cacheFactory);
        injectOnlyLenientScheme = new DefaultInstantiationScheme(injectOnlyLenientSelector, defaultServices, ImmutableSet.of(Inject.class), cacheFactory);
        decoratingScheme = new DefaultInstantiationScheme(decoratedJsr330Selector, defaultServices, ImmutableSet.of(Inject.class), cacheFactory);
        decoratingLenientScheme = new DefaultInstantiationScheme(decoratedLenientSelector, defaultServices, ImmutableSet.of(Inject.class), cacheFactory);
    }

    @Override
    public Instantiator inject(ServiceLookup services) {
        return injectOnlyScheme.withServices(services).instantiator();
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
        if (injectAnnotations.isEmpty()) {
            return injectOnlyScheme;
        }

        for (Class<? extends Annotation> annotation : injectAnnotations) {
            assertKnownAnnotation(annotation);
        }

        ClassGenerator classGenerator = AsmBackedClassGenerator.injectOnly(annotationHandlers, ImmutableSet.copyOf(injectAnnotations), cacheFactory, MANAGED_FACTORY_ID);
        Jsr330ConstructorSelector constructorSelector = new Jsr330ConstructorSelector(classGenerator, cacheFactory.newClassCache());
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builderWithExpectedSize(injectAnnotations.size() + 1);
        builder.addAll(injectAnnotations);
        builder.add(Inject.class);
        return new DefaultInstantiationScheme(constructorSelector, defaultServices, builder.build(), cacheFactory);
    }

    @Override
    public Instantiator injectLenient() {
        return injectOnlyLenientScheme.instantiator();
    }

    @Override
    public Instantiator injectLenient(ServiceLookup services) {
        return injectOnlyLenientScheme.withServices(services).instantiator();
    }

    @Override
    public Instantiator decorateLenient() {
        return decoratingLenientScheme.instantiator();
    }

    @Override
    public Instantiator injectAndDecorateLenient(ServiceLookup services) {
        return decoratingLenientScheme.withServices(services).instantiator();
    }

    @Override
    public InstantiationScheme decorateScheme() {
        return decoratingScheme;
    }

    @Override
    public Instantiator injectAndDecorate(ServiceLookup services) {
        return decoratingScheme.withServices(services).instantiator();
    }

    @Override
    public ManagedFactory getManagedFactory() {
        return managedFactory;
    }

    private void assertKnownAnnotation(Class<? extends Annotation> annotation) {
        for (InjectAnnotationHandler annotationHandler : annotationHandlers) {
            if (annotationHandler.getAnnotationType().equals(annotation)) {
                return;
            }
        }
        throw new IllegalArgumentException(String.format("Annotation @%s is not a registered injection annotation.", annotation.getSimpleName()));
    }

    private static class ClassGeneratorBackedManagedFactory implements ManagedFactory {
        private final ClassGenerator classGenerator;

        public ClassGeneratorBackedManagedFactory(ClassGenerator classGenerator) {
            this.classGenerator = classGenerator;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            Class<?> generatedClass = classGenerator.generate(type).getGeneratedClass();
            return new ManagedTypeFactory(generatedClass).fromState(type, state);
        }

        @Override
        public int getId() {
            return MANAGED_FACTORY_ID;
        }
    }
}
