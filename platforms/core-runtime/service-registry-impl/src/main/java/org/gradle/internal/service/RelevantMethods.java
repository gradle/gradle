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
package org.gradle.internal.service;

import org.gradle.api.specs.Spec;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.util.internal.CollectionUtils.any;

class RelevantMethods {
    private static final ConcurrentMap<Class<?>, RelevantMethods> METHODS_CACHE = new ConcurrentHashMap<Class<?>, RelevantMethods>();
    private static final ServiceMethodFactory SERVICE_METHOD_FACTORY = new DefaultServiceMethodFactory();

    final List<ServiceMethod> decorators;
    final List<ServiceMethod> factories;
    final List<ServiceMethod> configurers;

    private RelevantMethods(List<ServiceMethod> decorators, List<ServiceMethod> factories, List<ServiceMethod> configurers) {
        this.decorators = decorators;
        this.factories = factories;
        this.configurers = configurers;
    }

    public static RelevantMethods getMethods(Class<? extends ServiceRegistrationProvider> type) {
        RelevantMethods relevantMethods = METHODS_CACHE.get(type);
        if (relevantMethods == null) {
            relevantMethods = new RelevantMethodsBuilder(type).build();
            METHODS_CACHE.putIfAbsent(type, relevantMethods);
        }
        return relevantMethods;
    }

    private static class RelevantMethodsBuilder {
        private final Class<?> type;
        private final List<ServiceMethod> decorators = new ArrayList<ServiceMethod>();
        private final List<ServiceMethod> factories = new ArrayList<ServiceMethod>();
        private final List<ServiceMethod> configurers = new ArrayList<ServiceMethod>();

        private final Set<String> seen = new HashSet<String>();

        public RelevantMethodsBuilder(Class<? extends ServiceRegistrationProvider> type) {
            this.type = type;
        }

        public RelevantMethods build() {
            for (Class<?> clazz = type; clazz != Object.class && clazz != DefaultServiceRegistry.class; clazz = clazz.getSuperclass()) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    addMethod(method);
                }
            }
            return new RelevantMethods(decorators, factories, configurers);
        }

        private void addMethod(Method method) {
            if (method.getName().equals("configure")) {
                if (!method.getReturnType().equals(Void.TYPE)) {
                    throw new ServiceValidationException(String.format("Method %s.%s() must return void.", type.getName(), method.getName()));
                }
                add(configurers, method);
            } else if (method.getName().startsWith("create") || method.getName().startsWith("decorate")) {
                if (method.getAnnotation(Provides.class) == null) {
                    throw new ServiceValidationException(String.format("Method %s.%s() must be annotated with @Provides.", type.getName(), method.getName()));
                }
                if (method.getReturnType().equals(Void.TYPE)) {
                    throw new ServiceValidationException(String.format("Method %s.%s() must not return void.", type.getName(), method.getName()));
                }
                if (isDecorating(method)) {
                    add(decorators, method);
                } else {
                    add(factories, method);
                }
            } else if (method.getAnnotation(Provides.class) != null) {
                throw new ServiceValidationException(String.format("Non-factory method %s.%s() must not be annotated with @Provides.", type.getName(), method.getName()));
            }
        }

        public void add(List<ServiceMethod> builder, Method method) {
            StringBuilder signature = new StringBuilder();
            signature.append(method.getName());
            for (Class<?> parameterType : method.getParameterTypes()) {
                signature.append(",");
                signature.append(parameterType.getName());
            }
            if (seen.add(signature.toString())) {
                builder.add(SERVICE_METHOD_FACTORY.toServiceMethod(method));
            }
        }

        private static boolean isDecorating(Method method) {
            // Boilerplate due to Java 6 constraint
            Class<?>[] parameterTypes = method.getParameterTypes();
            int parameterCount = parameterTypes.length;
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            for (int i = 0; i < parameterCount; i++) {
                if (parameterTypes[i].equals(method.getReturnType())) {
                    boolean isFromConstructor = any(parameterAnnotations[i], new Spec<Annotation>() {
                        @Override
                        public boolean isSatisfiedBy(Annotation element) {
                            return FromConstructor.class.equals(element.annotationType());
                        }
                    });

                    if (isFromConstructor) {
                        continue;
                    }

                    return true;
                }
            }

            return false;
        }
    }
}
