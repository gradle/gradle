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

import org.gradle.internal.service.ServiceLookup;

import java.lang.annotation.Annotation;

/**
 * Creates instances of the given type. This is similar to {@link org.gradle.internal.reflect.Instantiator}, but produces instances of the given type only. This allows it to provides some
 * additional metadata about the type, such as which services it requires, and may be more performant at creating instances due to the extra context it holds.
 */
public interface InstanceFactory<T> {
    /**
     * Is the given service required to be injected by type?
     */
    boolean requiresService(Class<?> serviceType);

    /**
     * Is any service injection triggered by the given annotation?
     */
    boolean serviceInjectionTriggeredByAnnotation(Class<? extends Annotation> injectAnnotation);

    /**
     * Creates a new instance from the given services and parameters.
     */
    T newInstance(ServiceLookup services, Object... params);

    /**
     * Creates a new instance from the given parameters and the default services.
     */
    T newInstance(Object... params);
}
