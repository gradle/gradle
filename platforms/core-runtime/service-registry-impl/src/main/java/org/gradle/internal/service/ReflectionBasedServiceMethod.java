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
import org.gradle.internal.reflect.JavaMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

class ReflectionBasedServiceMethod extends AbstractServiceMethod {
    private final JavaMethod<Object, Object> javaMethod;

    ReflectionBasedServiceMethod(Method target) {
        super(target);
        javaMethod = JavaMethod.of(Object.class, target);
    }

    @Override
    public Object invoke(Object target, @Nullable Object... args) {
        try {
            return javaMethod.invoke(target, args);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public String toString() {
        return javaMethod.toString();
    }
}
