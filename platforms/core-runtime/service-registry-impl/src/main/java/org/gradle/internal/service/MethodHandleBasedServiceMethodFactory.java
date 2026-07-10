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

import org.gradle.internal.UncheckedException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class MethodHandleBasedServiceMethodFactory implements ServiceMethodFactory {

    public MethodHandleBasedServiceMethodFactory() {
        try {
            // Force loading to check if method handle is supported
            Class.forName("java.lang.invoke.MethodHandle");
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public ServiceMethod toServiceMethod(Method method) {
        if (Modifier.isPublic(method.getModifiers()) && Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            try {
                return new MethodHandleBasedServiceMethod(method);
            } catch (IllegalAccessException ex) {
                return new ReflectionBasedServiceMethod(method);
            }
        }
        return new ReflectionBasedServiceMethod(method);
    }
}
