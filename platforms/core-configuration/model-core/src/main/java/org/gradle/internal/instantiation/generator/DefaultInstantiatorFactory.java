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

package org.gradle.internal.instantiation.generator;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.instantiation.DeserializationInstantiator;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
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
    private static final int MANAGED_FACTORY_ID = Objects.hashCode(ManagedTypeFactory.class.getName());

    private final ServiceRegistry defaultServices;
    private final CrossBuildInMemoryCacheFactory cacheFactory;
    private final List<InjectAnnotationHandler> annotationHandlers;
    private final PropertyRoleAnnotationHandler roleHandler;
    private final DefaultInstantiationScheme injectOnlyScheme;
    private final DefaultInstantiationScheme injectOnlyLenientScheme;
    private final DefaultInstantiationScheme decoratingScheme;
    private final DefaultInstantiationScheme decoratingLenientScheme;
    private final ManagedFactory managedFactory;

    public DefaultInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory, List<InjectAnnotationHandler> injectHandlers, PropertyRoleAnnotationHandler roleAnnotationHandler) {
        this.cacheFactory = cacheFactory;
        this.annotationHandlers = injectHandlers;
        this.roleHandler = roleAnnotationHandler;
        this.defaultServices = defaultServiceRegistry();
        ClassGenerator injectOnlyGenerator = AsmBackedClassGenerator.injectOnly(injectHandlers, roleAnnotationHandler, ImmutableSet.of(), cacheFactory, MANAGED_FACTORY_ID);
        ClassGenerator decoratedGenerator = AsmBackedClassGenerator.decorateAndInject(injectHandlers, roleAnnotationHandler, ImmutableSet.of(), cacheFactory, MANAGED_FACTORY_ID);
        ConstructorSelector injectOnlyJsr330Selector = new Jsr330ConstructorSelector(injectOnlyGenerator, cacheFactory.newClassCache());
        ConstructorSelector decoratedJsr330Selector = new Jsr330ConstructorSelector(decoratedGenerator, cacheFactory.newClassCache());
        ConstructorSelector injectOnlyLenientSelector = new ParamsMatchingConstructorSelector(injectOnlyGenerator);
        ConstructorSelector decoratedLenientSelector = new ParamsMatchingConstructorSelector(decoratedGenerator);
        ImmutableSet<Class<? extends Annotation>> injectionAnnotations = ImmutableSet.of(Inject.class);
        this.injectOnlyScheme = new DefaultInstantiationScheme(injectOnlyJsr330Selector, injectOnlyGenerator, defaultServices, injectionAnnotations, cacheFactory);
        this.injectOnlyLenientScheme = new DefaultInstantiationScheme(injectOnlyLenientSelector, injectOnlyGenerator, defaultServices, injectionAnnotations, cacheFactory);
        this.decoratingScheme = new DefaultInstantiationScheme(decoratedJsr330Selector, decoratedGenerator, defaultServices, injectionAnnotations, cacheFactory);
        this.decoratingLenientScheme = new DefaultInstantiationScheme(decoratedLenientSelector, decoratedGenerator, defaultServices, injectionAnnotations, cacheFactory);
        this.managedFactory = new ManagedTypeFactory(injectOnlyScheme.deserializationInstantiator());
    }

    private DefaultServiceRegistry defaultServiceRegistry() {
        DefaultServiceRegistry services = new DefaultServiceRegistry("default services");
        services.add(InstantiatorFactory.class, this);
        return services;
    }

    @Override
    public InstanceGenerator inject(ServiceLookup services) {
        return injectOnlyScheme.withServices(services).instantiator();
    }

    @Override
    public InstanceGenerator inject() {
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

        ClassGenerator classGenerator = AsmBackedClassGenerator.injectOnly(annotationHandlers, roleHandler, ImmutableSet.copyOf(injectAnnotations), cacheFactory, MANAGED_FACTORY_ID);
        Jsr330ConstructorSelector constructorSelector = new Jsr330ConstructorSelector(classGenerator, cacheFactory.newClassCache());
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builderWithExpectedSize(injectAnnotations.size() + 1);
        builder.addAll(injectAnnotations);
        builder.add(Inject.class);
        return new DefaultInstantiationScheme(constructorSelector, classGenerator, defaultServices, builder.build(), cacheFactory);
    }

    @Override
    public InstanceGenerator injectLenient() {
        return injectOnlyLenientScheme.instantiator();
    }

    @Override
    public InstanceGenerator injectLenient(ServiceLookup services) {
        return injectOnlyLenientScheme.withServices(services).instantiator();
    }

    @Override
    public InstantiationScheme decorateLenientScheme() {
        return decoratingLenientScheme;
    }

    @Override
    public InstanceGenerator decorateLenient() {
        return decoratingLenientScheme.instantiator();
    }

    @Override
    public InstanceGenerator decorateLenient(ServiceLookup services) {
        return decoratingLenientScheme.withServices(services).instantiator();
    }

    @Override
    public InstantiationScheme decorateScheme() {
        return decoratingScheme;
    }

    @Override
    public InstanceGenerator decorate(ServiceLookup services) {
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

    private static class ManagedTypeFactory implements ManagedFactory {

        private final DeserializationInstantiator deserializationInstantiator;

        public ManagedTypeFactory(DeserializationInstantiator deserializationInstantiator) {
            this.deserializationInstantiator = deserializationInstantiator;
        }

        @Nullable
        @Override
        public <T> T fromState(Class<T> type, Object state) {
            try {
                Object instance = deserializationInstantiator.newInstance(type, Object.class);
                instance.getClass().getMethod("initFromState", Object[].class).invoke(instance, state);
                return type.cast(instance);
            } catch (Exception e) {
                throw new ObjectInstantiationException(type, e);
            }
        }

        @Override
        public int getId() {
            return MANAGED_FACTORY_ID;
        }
    }
}
