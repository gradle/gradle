/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.apache.commons.lang.ClassUtils;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class VirtualComponentHelper {
    /**
     * Decorates a component identifier, marking it as virtual.
     * @param id the original component identifier
     * @return a virtual component identifier
     */
    public static VirtualComponentIdentifier makeVirtual(final ComponentIdentifier id) {
        Class<? extends ComponentIdentifier> cidClazz = id.getClass();
        List<Class<?>> allInterfaces = Cast.uncheckedCast(ClassUtils.getAllInterfaces(cidClazz));
        allInterfaces.add(VirtualComponentIdentifier.class);
        return Cast.uncheckedCast(Proxy.newProxyInstance(cidClazz.getClassLoader(), allInterfaces.toArray(new Class<?>[0]), new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return method.invoke(id, args);
            }
        }));
    }
}
