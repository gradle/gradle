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

import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * Provides various mechanisms for instantiation of objects.
 *
 * <p>A service of this type is available in all scopes and is the recommended way to obtain an {@link Instantiator} and other types.</p>
 */
public interface InstantiatorFactory {
    /**
     * Creates an {@link Instantiator} that can inject services and user provided values into the instances it creates, but does not decorate the instances.
     *
     * <p>Use for any non-model types for which services or user provided constructor values need to injected.
     *
     * @param services The services to make available to instances.
     */
    Instantiator inject(ServiceLookup services);

    /**
     * Creates an {@link Instantiator} that can inject user provided values into the instances it creates, but does not decorate the instances.
     *
     * <p>Use for any non-model types for which user provided values, but no services, need to be injected. This is simply a convenience for {@link #inject(ServiceLookup)}.
     */
    Instantiator inject();

    /**
     * Create a new {@link InstantiationScheme} that can inject services and user provided values into the instances it creates, but does not decorate the instances. Supports using the {@link javax.inject.Inject} annotation plus additional custom annotations.
     *
     * @param injectAnnotations Zero or more annotations that mark properties whose value will be injected on creation. Each annotation must be known to this factory via a {@link InjectAnnotationHandler}.
     */
    InstantiationScheme injectScheme(Collection<Class<? extends Annotation>> injectAnnotations);

    /**
     * Creates an {@link Instantiator} that can inject user provided values into the instances it creates, but does not decorate the instances.
     * The returned {@link Instantiator} is lenient when there is a missing {@link javax.inject.Inject} annotation or null constructor parameters,
     * for backwards compatibility.
     *
     * <p>Use for any non-model types for which user provided values and services need to be injected. Use this method only for existing types
     * where backwards compatibility is required and instead prefer {@link #inject(ServiceLookup)} for any new non DSL-types.
     * This method will be retired in the future.
     */
    Instantiator injectLenient(ServiceLookup services);

    /**
     * Creates an {@link Instantiator} that can inject user provided values into the instances it creates, but does not decorate the instances.
     * The returned {@link Instantiator} is lenient when there is a missing {@link javax.inject.Inject} annotation or null constructor parameters,
     * for backwards compatibility.
     *
     * <p>Use for any non-model types for which user provided values, but no services, need to be injected. Use this method only for existing types
     * where backwards compatibility is required and instead prefer {@link #inject()} for any new non DSL-types.
     * This method will be retired in the future.
     */
    Instantiator injectLenient();

    /**
     * Creates an {@link Instantiator} that decorates the instances created.
     *
     * <p>Use for any model types for which no user provided constructor values or services need to be injected. This is a convenience for {@link #injectAndDecorateLenient(ServiceLookup)} and will also be retired.
     */
    Instantiator decorateLenient();

    /**
     * Creates an {@link Instantiator} that can inject services and user provided values into the instances it creates and also decorates the instances.
     *
     * <p>Use for any model types for which services or user provided constructor values need to injected.
     *
     * @param services The services to make available to instances.
     */
    Instantiator injectAndDecorate(ServiceLookup services);

    /**
     * Creates an {@link Instantiator} that can inject user provided values into the instances it creates and also decorates the instances.
     *
     * <p>Use for any model types for which services or user provided constructor values need to injected.
     */
    Instantiator injectAndDecorate();

    /**
     * Creates an {@link Instantiator} that can inject services and user provided values into the instances it creates and also decorates the instances.
     * The returned {@link Instantiator} is lenient when there is a missing {@link javax.inject.Inject} annotation or null constructor parameters,
     * for backwards compatibility.
     *
     * <p>Use for any model types for which services or user provided constructor values need to injected. Use this method only for existing types
     * where backwards compatibility is required and instead prefer {@link #injectAndDecorate(ServiceLookup)} for any new non DSL-types.
     * This method will be retired in the future.
     *
     * @param services The registry of services to make available to instances.
     */
    Instantiator injectAndDecorateLenient(ServiceLookup services);
}
