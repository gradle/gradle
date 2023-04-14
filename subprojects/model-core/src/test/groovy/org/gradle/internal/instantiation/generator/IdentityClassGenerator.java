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

import com.google.common.collect.Ordering;
import org.gradle.api.Describable;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instantiation.ClassGenerationException;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.service.ServiceLookup;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

class IdentityClassGenerator implements ClassGenerator {
    @Override
    public <T> GeneratedClass<? extends T> generate(final Class<T> type) throws ClassGenerationException {
        return new GeneratedClass<T>() {
            @Override
            public Class<T> getGeneratedClass() {
                return type;
            }

            @Nullable
            @Override
            public Class<?> getOuterType() {
                if (Modifier.isStatic(type.getModifiers())) {
                    return null;
                } else {
                    return type.getEnclosingClass();
                }
            }

            @Override
            public SerializationConstructor<T> getSerializationConstructor(Class<? super T> baseClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GeneratedConstructor<T>> getConstructors() {
                List<GeneratedConstructor<T>> constructors = new ArrayList<GeneratedConstructor<T>>();
                for (final Constructor<?> constructor : type.getDeclaredConstructors()) {
                    constructors.add(new GeneratedConstructor<T>() {
                        @Override
                        public T newInstance(ServiceLookup services, InstanceGenerator nested, @Nullable Describable displayName, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
                            return DeprecationLogger.whileDisabledThrowing(() -> {
                                constructor.setAccessible(true);
                                return type.cast(constructor.newInstance(params));
                            });
                        }

                        @Override
                        public boolean requiresService(Class<?> serviceType) {
                            return false;
                        }

                        @Override
                        public boolean serviceInjectionTriggeredByAnnotation(Class<? extends Annotation> serviceAnnotation) {
                            return false;
                        }

                        @Override
                        public Class<?>[] getParameterTypes() {
                            return constructor.getParameterTypes();
                        }

                        @Override
                        public Type[] getGenericParameterTypes() {
                            return constructor.getGenericParameterTypes();
                        }

                        @Nullable
                        @Override
                        public <S extends Annotation> S getAnnotation(Class<S> annotation) {
                            return constructor.getAnnotation(annotation);
                        }

                        @Override
                        public int getModifiers() {
                            return constructor.getModifiers();
                        }
                    });
                }
                return Ordering.from(new ConstructorComparator()).sortedCopy(constructors);
            }
        };
    }
}
