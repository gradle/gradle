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
import java.lang.reflect.Type;

abstract class AbstractServiceMethod implements ServiceMethod {
    private final Method method;
    private final Class<?> owner;
    private final String name;
    private final Type[] parameterTypes;
    private final Type serviceType;

    AbstractServiceMethod(Method target) {
        this.method = target;
        this.owner = target.getDeclaringClass();
        this.name = target.getName();
        this.parameterTypes = target.getGenericParameterTypes();
        this.serviceType = target.getGenericReturnType();
    }

    @Override
    public Type getServiceType() {
        return serviceType;
    }

    @Override
    public Type[] getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public Class<?> getOwner() {
        return owner;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Method getMethod() {
        return method;
    }
}
