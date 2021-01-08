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

import org.gradle.api.Describable;
import org.gradle.internal.instantiation.ClassGenerationException;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.service.ServiceLookup;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.List;

interface ClassGenerator {
    /**
     * Generates a proxy class for the given class. May return the given class unmodified or may generate a subclass.
     *
     * <p>Implementation should ensure that it is efficient to call this method multiple types for the same class.</p>
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

        /**
         * Creates a serialization constructor. Note: this can be expensive and does not perform any caching.
         */
        SerializationConstructor<T> getSerializationConstructor(Class<? super T> baseClass);
    }

    interface SerializationConstructor<T> {
        /**
         * Creates a new instance, using the given services and parameters. Uses the given instantiator to create nested objects, if required.
         */
        T newInstance(ServiceLookup services, InstanceGenerator nested) throws InvocationTargetException, IllegalAccessException, InstantiationException;
    }

    interface GeneratedConstructor<T> {
        /**
         * Creates a new instance, using the given services and parameters. Uses the given instantiator to create nested objects, if required.
         */
        T newInstance(ServiceLookup services, InstanceGenerator nested, @Nullable Describable displayName, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException;

        /**
         * Does this constructor use the given service type?
         */
        boolean requiresService(Class<?> serviceType);

        /**
         * Does this constructor use a service injected via the given annotation?
         */
        boolean serviceInjectionTriggeredByAnnotation(Class<? extends Annotation> serviceAnnotation);

        Class<?>[] getParameterTypes();

        Type[] getGenericParameterTypes();

        @Nullable
        <S extends Annotation> S getAnnotation(Class<S> annotation);

        int getModifiers();
    }

}
