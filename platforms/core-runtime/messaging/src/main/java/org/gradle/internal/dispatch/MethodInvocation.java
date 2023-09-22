/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.dispatch;

import org.gradle.util.internal.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

public class MethodInvocation {
    private static final Object[] ZERO_ARGS = new Object[0];
    private final Method method;
    private final Object[] arguments;

    public MethodInvocation(Method method, Object[] args) {
        this.method = method;
        arguments = args == null ? ZERO_ARGS : args;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        MethodInvocation other = (MethodInvocation) obj;
        if (!method.equals(other.method)) {
            return false;
        }

        return Arrays.equals(arguments, other.arguments);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    @Override
    public String toString() {
        return String.format("[MethodInvocation method: %s(%s)]", method.getName(), CollectionUtils.join(", ", arguments));
    }
}

