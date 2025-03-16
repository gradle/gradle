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
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

class MethodHandleBasedServiceMethod extends AbstractServiceMethod {
    private final static MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private final MethodHandle method;

    MethodHandleBasedServiceMethod(Method target) throws IllegalAccessException {
        super(target);
        this.method = LOOKUP.unreflect(target);
    }

    @Override
    public Object invoke(Object target, @Nullable Object... args) {
        try {
            return method.bindTo(target).invokeWithArguments(args);
        } catch (Throwable e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
