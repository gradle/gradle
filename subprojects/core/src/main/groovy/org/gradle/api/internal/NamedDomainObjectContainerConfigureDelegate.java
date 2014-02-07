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
import org.gradle.api.NamedDomainObjectContainer;

public class NamedDomainObjectContainerConfigureDelegate extends ConfigureDelegate {
    private final NamedDomainObjectContainer _container;

    public NamedDomainObjectContainerConfigureDelegate(Object owner, NamedDomainObjectContainer container) {
        super(owner, container);
        _container = container;
    }

    @Override
    protected boolean _isConfigureMethod(String name, Object[] params) {
        return params.length == 1 && params[0] instanceof Closure;
    }

    @Override
    protected Object _configure(String name, Object[] params) {
        if (params.length == 0) {
            return _container.create(name);
        }
        return _container.create(name, (Closure) params[0]);
    }
}