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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.util.internal.ArrayUtils.contains;

class RelevantMethods {
    @SuppressWarnings("unchecked")
    private static final ClassValue<RelevantMethods> METHODS_CACHE = new ClassValue<RelevantMethods>() {
        @Override
        protected RelevantMethods computeValue(Class<?> type) {
            return new RelevantMethodsBuilder((Class<? extends ServiceRegistrationProvider>) type).build();
        }
    };
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
        return METHODS_CACHE.get(type);
    }

    private static class RelevantMethodsBuilder {
        private final Class<?> type;
        private final List<ServiceMethod> decorators = new ArrayList<>();
        private final List<ServiceMethod> factories = new ArrayList<>();
        private final List<ServiceMethod> configurers = new ArrayList<>();

        private final Set<String> seen = new HashSet<>();

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
                if (takesReturnTypeAsParameter(method)) {
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

        private static boolean takesReturnTypeAsParameter(Method method) {
            return contains(method.getParameterTypes(), method.getReturnType());
        }
    }
}
