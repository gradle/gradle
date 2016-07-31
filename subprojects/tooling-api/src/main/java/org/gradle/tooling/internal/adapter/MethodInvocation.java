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

package org.gradle.tooling.internal.adapter;

import java.lang.reflect.Type;

class MethodInvocation {
    private final Object[] parameters;
    private final Class returnType;
    private final Type genericReturnType;
    private final String name;
    private final Class<?>[] parameterTypes;
    private final Object view;
    private final Class<?> viewType;
    private final Object delegate;

    private Object result;
    private boolean found;

    MethodInvocation(String name, Class returnType, Type genericReturnType, Class<?>[] parameterTypes, Object view, Class<?> viewType, Object delegate, Object[] parameters) {
        this.name = name;
        this.returnType = returnType;
        this.genericReturnType = genericReturnType;
        this.parameterTypes = parameterTypes;
        this.view = view;
        this.viewType = viewType;
        this.delegate = delegate;
        this.parameters = parameters;
    }

    public boolean isGetter() {
        return parameterTypes.length == 0 && isIsOrGet();
    }

    public boolean isIsOrGet() {
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    public Object[] getParameters() {
        return parameters;
    }

    public Class getReturnType() {
        return returnType;
    }

    public Type getGenericReturnType() {
        return genericReturnType;
    }

    /**
     * Marks the method as handled.
     */
    public void setResult(Object result) {
        found = true;
        this.result = result;
    }

    public Object getResult() {
        return result;
    }

    public String getName() {
        return name;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public boolean found() {
        return found;
    }

    public Class<?> getViewType() {
        return viewType;
    }

    public Object getView() {
        return view;
    }

    public Object getDelegate() {
        return delegate;
    }
}
