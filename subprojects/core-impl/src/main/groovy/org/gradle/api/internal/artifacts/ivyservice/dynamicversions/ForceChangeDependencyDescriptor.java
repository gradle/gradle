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
package org.gradle.api.internal.artifacts.ivyservice.dynamicversions;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ForceChangeDependencyDescriptor {
    public static DependencyDescriptor forceChangingFlag(DependencyDescriptor delegate, boolean isChanging) {
        ChangingDependencyDescriptorInvocationHandler invocationHandler = new ChangingDependencyDescriptorInvocationHandler(delegate, isChanging);
        return DependencyDescriptor.class.cast(Proxy.newProxyInstance(DependencyDescriptor.class.getClassLoader(), new Class<?>[]{DependencyDescriptor.class}, invocationHandler));
    }

    private static class ChangingDependencyDescriptorInvocationHandler implements InvocationHandler {
        private final DependencyDescriptor delegate;
        private final boolean changing;

        private ChangingDependencyDescriptorInvocationHandler(DependencyDescriptor delegate, boolean isChanging) {
            this.delegate = delegate;
            this.changing = isChanging;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("isChanging")) {
                return changing;
            }
            return method.invoke(delegate, args);
        }
    }

}
