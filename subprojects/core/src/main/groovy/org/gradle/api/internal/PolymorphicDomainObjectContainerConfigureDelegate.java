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

import org.gradle.api.PolymorphicDomainObjectContainer;

import groovy.lang.Closure;

public class PolymorphicDomainObjectContainerConfigureDelegate extends NamedDomainObjectContainerConfigureDelegate {
    private final PolymorphicDomainObjectContainer container;

    public PolymorphicDomainObjectContainerConfigureDelegate(Object owner, PolymorphicDomainObjectContainer container) {
        super(owner, container);
        this.container = container;
    }

    @Override
    protected boolean _isConfigureMethod(String name, Object[] params) {
        return super._isConfigureMethod(name, params)
                || params.length == 1 && params[0] instanceof Class
                || params.length == 2 && params[0] instanceof Class && params[1] instanceof Closure;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void _configure(String name, Object[] params) {
        if (params.length > 0 && params[0] instanceof Class) {
            container.create(name, (Class) params[0]);
        } else {
            container.create(name);
        }
    }
}