/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.util.internal.ConfigureUtil;

public class PolymorphicDomainObjectContainerConfigureDelegate<T> extends ConfigureDelegate {
    private final PolymorphicDomainObjectContainer<T> _container;

    public PolymorphicDomainObjectContainerConfigureDelegate(Closure<?> configureClosure, PolymorphicDomainObjectContainer<T> container) {
        super(configureClosure, container);
        this._container = container;
    }

    @Override
    protected DynamicInvokeResult _configure(String name) {
        return DynamicInvokeResult.found(_container.create(name));
    }

    @Override
    protected DynamicInvokeResult _configure(String name, Object[] params) {
        if (params.length == 1 && params[0] instanceof Closure) {
            return DynamicInvokeResult.found(_container.create(name, (Closure<?>) params[0]));
        } else if (params.length == 1 && params[0] instanceof Class) {
            return DynamicInvokeResult.found(_container.create(name, Cast.<Class<T>>uncheckedCast(params[0])));
        } else if (params.length == 2 && params[0] instanceof Class && params[1] instanceof Closure){
            return DynamicInvokeResult.found(_container.create(name, Cast.uncheckedCast(params[0]), ConfigureUtil.configureUsing((Closure<?>) params[1])));
        }
        return DynamicInvokeResult.notFound();
    }
}
