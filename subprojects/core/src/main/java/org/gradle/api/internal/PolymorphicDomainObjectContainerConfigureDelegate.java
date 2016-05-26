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
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.metaobject.GetPropertyResult;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.util.ConfigureUtil;

public class PolymorphicDomainObjectContainerConfigureDelegate extends ConfigureDelegate {
    private final PolymorphicDomainObjectContainer _container;

    public PolymorphicDomainObjectContainerConfigureDelegate(Closure configureClosure, PolymorphicDomainObjectContainer container) {
        super(configureClosure, container);
        this._container = container;
    }

    @Override
    protected void _configure(String name, GetPropertyResult result) {
        result.result(_container.create(name));
    }

    @Override
    protected void _configure(String name, Object[] params, InvokeMethodResult result) {
        if (params.length == 1 && params[0] instanceof Closure) {
            result.result(_container.create(name, (Closure) params[0]));
        } else if (params.length == 1 && params[0] instanceof Class) {
            result.result(_container.create(name, (Class) params[0]));
        } else if (params.length == 2 && params[0] instanceof Class && params[1] instanceof Closure){
            result.result(_container.create(name, (Class) params[0], ConfigureUtil.configureUsing((Closure) params[1])));
        }
    }
}
