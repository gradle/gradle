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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RelevantMethods {
    private static final ConcurrentMap<Class<?>, RelevantMethods> METHODS_CACHE = new ConcurrentHashMap<Class<?>, RelevantMethods>();
    private static final ServiceMethodFactory SERVICE_METHOD_FACTORY = new DefaultServiceMethodFactory();

    final List<ServiceMethod> decorators;
    final List<ServiceMethod> factories;
    final List<ServiceMethod> configurers;

    RelevantMethods(List<Method> decorators, List<Method> factories, List<Method> configurers) {
        this.decorators = toServiceMethodList(decorators);
        this.factories = toServiceMethodList(factories);
        this.configurers = toServiceMethodList(configurers);
    }

    private static List<ServiceMethod> toServiceMethodList(List<Method> methods) {
        List<ServiceMethod> result = new ArrayList<ServiceMethod>(methods.size());
        for (Method method : methods) {
            result.add(SERVICE_METHOD_FACTORY.toServiceMethod(method));
        }
        return result;
    }

    public static RelevantMethods getMethods(Class<?> type) {
        RelevantMethods relevantMethods = METHODS_CACHE.get(type);
        if (relevantMethods == null) {
            relevantMethods = buildRelevantMethods(type);
            METHODS_CACHE.putIfAbsent(type, relevantMethods);
        }

        return relevantMethods;
    }

    private static RelevantMethods buildRelevantMethods(Class<?> type) {
        RelevantMethodsBuilder builder = new RelevantMethodsBuilder(type);
        RelevantMethods relevantMethods;
        addDecoratorMethods(builder);
        addFactoryMethods(builder);
        addConfigureMethods(builder);
        relevantMethods = builder.build();
        return relevantMethods;
    }

    private static void addConfigureMethods(RelevantMethodsBuilder builder) {
        Class<?> type = builder.type;
        Iterator<Method> iterator = builder.remainingMethods.iterator();
        while (iterator.hasNext()) {
            Method method = iterator.next();
            if (method.getName().equals("configure")) {
                if (!method.getReturnType().equals(Void.TYPE)) {
                    throw new ServiceLookupException(String.format("Method %s.%s() must return void.", type.getSimpleName(), method.getName()));
                }
                builder.add(iterator, builder.configurers, method);
            }
        }
    }

    private static void addFactoryMethods(RelevantMethodsBuilder builder) {
        Class<?> type = builder.type;
        Iterator<Method> iterator = builder.remainingMethods.iterator();
        while (iterator.hasNext()) {
            Method method = iterator.next();
            if (method.getName().startsWith("create") && !Modifier.isStatic(method.getModifiers())) {
                if (method.getReturnType().equals(Void.TYPE)) {
                    throw new ServiceLookupException(String.format("Method %s.%s() must not return void.", type.getSimpleName(), method.getName()));
                }
                builder.add(iterator, builder.factories, method);
            }
        }
    }

    private static void addDecoratorMethods(RelevantMethodsBuilder builder) {
        Class<?> type = builder.type;
        Iterator<Method> iterator = builder.remainingMethods.iterator();
        while (iterator.hasNext()) {
            Method method = iterator.next();
            if (method.getName().startsWith("create") || method.getName().startsWith("decorate")) {
                if (method.getReturnType().equals(Void.TYPE)) {
                    throw new ServiceLookupException(String.format("Method %s.%s() must not return void.", type.getSimpleName(), method.getName()));
                }
                if (takesReturnTypeAsParameter(method)) {
                    builder.add(iterator, builder.decorators, method);
                }
            }
        }
    }

    private static boolean takesReturnTypeAsParameter(Method method) {
        for (Class<?> param : method.getParameterTypes()) {
            if (param.equals(method.getReturnType())) {
                return true;
            }
        }
        return false;
    }

}
