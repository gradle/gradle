/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.listener;

import groovy.lang.Closure;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;

import java.util.Arrays;

public class ClosureBackedMethodInvocationDispatch implements Dispatch<MethodInvocation> {
    private final String methodName;
    private final Closure closure;

    public ClosureBackedMethodInvocationDispatch(String methodName, Closure closure) {
        this.methodName = methodName;
        this.closure = closure;
    }

    @Override
    public void dispatch(MethodInvocation message) {
        if (message.getMethodName().equals(methodName)) {
            Object[] parameters = message.getArguments();
            if (closure.getMaximumNumberOfParameters() < parameters.length) {
                parameters = Arrays.asList(parameters).subList(0, closure.getMaximumNumberOfParameters()).toArray();
            }
            closure.call(parameters);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClosureBackedMethodInvocationDispatch that = (ClosureBackedMethodInvocationDispatch) o;

        if (!closure.equals(that.closure)) {
            return false;
        }
        if (!methodName.equals(that.methodName)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = methodName.hashCode();
        result = 31 * result + closure.hashCode();
        return result;
    }
}
