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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class RelevantMethodsBuilder {
    final List<Method> remainingMethods;
    final Class<?> type;
    final LinkedList<Method> decorators = new LinkedList<Method>();
    final LinkedList<Method> factories = new LinkedList<Method>();
    final LinkedList<Method> configurers = new LinkedList<Method>();

    private final Set<String> seen = new HashSet<String>();

    RelevantMethodsBuilder(Class<?> type) {
        this.type = type;
        this.remainingMethods = new LinkedList<Method>();

        for (Class<?> clazz = type; clazz != Object.class && clazz != DefaultServiceRegistry.class; clazz = clazz.getSuperclass()) {
            remainingMethods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
        }
    }

    void add(Iterator<Method> iterator, List<Method> builder, Method method) {
        StringBuilder signature = new StringBuilder();
        signature.append(method.getName());
        for (Class<?> parameterType : method.getParameterTypes()) {
            signature.append(",");
            signature.append(parameterType.getName());
        }
        if (seen.add(signature.toString())) {
            builder.add(method);
        }
        iterator.remove();
    }

    RelevantMethods build() {
        return new RelevantMethods(decorators, factories, configurers);
    }
}
