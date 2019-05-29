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

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.List;

interface ClassGenerator {
    /**
     * Generates a proxy class for the given class. May return the given class unmodified or may generate a subclass.
     */
    <T> GeneratedClass<? extends T> generate(Class<T> type) throws ClassGenerationException;

    interface GeneratedClass<T> {
        Class<T> getGeneratedClass();

        /**
         * Returns the enclosing type, when this type is a non-static inner class.
         */
        @Nullable
        Class<?> getOuterType();

        List<GeneratedConstructor<T>> getConstructors();
    }

    interface GeneratedConstructor<T> {
        /**
         * Creates a new instance, using the given services and parameters. Uses the given instantiator to create nested objects, if required.
         */
        T newInstance(ServiceLookup services, Instantiator nested, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException;

        /**
         * Does this constructor use the given service type?
         */
        boolean requiresService(Class<?> serviceType);

        /**
         * Does this constructor use a service injected via the given annotation?
         */
        boolean serviceInjectionTriggeredByAnnotation(Class<? extends Annotation> serviceAnnotation);

        //TODO:instant-execution: remove this
        Class<?> getGeneratedClass();

        Class<?>[] getParameterTypes();

        Type[] getGenericParameterTypes();

        @Nullable
        <S extends Annotation> S getAnnotation(Class<S> annotation);

        int getModifiers();
    }

}
