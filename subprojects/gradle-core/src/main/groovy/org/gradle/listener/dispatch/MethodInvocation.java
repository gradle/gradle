/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.listener.dispatch;

import java.lang.reflect.Method;

public class MethodInvocation extends Message {
    private transient Method method;
    private String methodName;
    private Class[] parameters;
    private Object[] arguments;

    public MethodInvocation(Method method, Object[] args) {
        this.method = method;
        methodName = method.getName();
        parameters = method.getParameterTypes();
        arguments = args;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public Method getMethod(Class<?> type) throws NoSuchMethodException {
        if (method != null) {
            return method;
        }
        return type.getMethod(methodName, parameters);
    }
}

