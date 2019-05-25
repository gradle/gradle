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
package org.gradle.api.internal;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.internal.reflect.Instantiator;

public class ReflectiveNamedDomainObjectFactory<T> implements NamedDomainObjectFactory<T> {
    private final Class<? extends T> type;
    private final Object[] extraArgs;
    private final Instantiator instantiator;

    public ReflectiveNamedDomainObjectFactory(Class<? extends T> type, Instantiator instantiator, Object... extraArgs) {
        this.type = type;
        this.instantiator = instantiator;
        this.extraArgs = extraArgs;
    }

    @Override
    public T create(String name) {
        return instantiator.newInstance(type, combineInstantiationArgs(name));
    }

    protected Object[] combineInstantiationArgs(String name) {
        Object[] combinedArgs;
        if (extraArgs.length == 0) {
            Object[] nameArg = {name};
            combinedArgs = nameArg;
        } else {
            combinedArgs = new Object[extraArgs.length + 1];
            combinedArgs[0] = name;
            int i = 1;
            for (Object e : extraArgs) {
                combinedArgs[i++] = e;
            }
        }

        return combinedArgs;
    }
}
