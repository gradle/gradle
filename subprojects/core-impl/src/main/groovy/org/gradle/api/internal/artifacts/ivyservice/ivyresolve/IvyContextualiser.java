/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.resolve.ResolveData;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class IvyContextualiser {
    private final Ivy ivy;
    private final ResolveData resolveData;

    public IvyContextualiser(Ivy ivy, ResolveData resolveData) {
        this.ivy = ivy;
        this.resolveData = resolveData;
    }
    
    public <T> T contextualise(Class<T> type, final T delegate) {
        Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{type}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                IvyContext context = IvyContext.pushNewCopyContext();
                try {
                    context.setIvy(ivy);
                    context.setResolveData(resolveData);
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw UncheckedException.throwAsUncheckedException(e.getTargetException());
                } finally {
                    IvyContext.popContext();
                }
            }
        });
        return type.cast(proxy);
    }

    public static IvyContext getIvyContext() {
        IvyContext context = IvyContext.getContext();
        if (context.peekIvy() == null || context.getResolveData() == null) {
            throw new IllegalStateException("Ivy context not established");
        }
        return context;
    }
}
