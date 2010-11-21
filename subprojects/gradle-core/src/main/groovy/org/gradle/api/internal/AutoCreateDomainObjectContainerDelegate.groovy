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
package org.gradle.api.internal

class AutoCreateDomainObjectContainerDelegate {
    private final Object owner;
    private final AutoCreateDomainObjectContainer container;
    private final DynamicObject delegate;
    private final ThreadLocal<Boolean> configuring = new ThreadLocal<Boolean>()

    public AutoCreateDomainObjectContainerDelegate(Object owner, AutoCreateDomainObjectContainer container) {
        this.container = container
        delegate = container.asDynamicObject
        this.owner = owner
    }

    @SuppressWarnings("EmptyCatchBlock")
    public Object invokeMethod(String name, Object params) {
        boolean isTopLevelCall = !configuring.get()
        configuring.set(true)
        try {
            if (delegate.hasMethod(name, params)) {
                return delegate.invokeMethod(name, params)
            }

            // try the owner
            try {
                return owner.invokeMethod(name, params)
            } catch (groovy.lang.MissingMethodException e) {
                // ignore
            }

            boolean isConfigureMethod = params.length == 1 && params[0] instanceof Closure
            if (isTopLevelCall && isConfigureMethod) {
                // looks like a configure method - add the object and try the delegate again
                container.add(name)
            }

            return delegate.invokeMethod(name, params);
        } finally {
            configuring.set(!isTopLevelCall)
        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    public Object get(String name) {
        if (delegate.hasProperty(name)) {
            return delegate.getProperty(name)
        }

        // try the owner
        try {
            return owner."$name"
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        // try the delegate again
        container.add(name)
        return delegate.getProperty(name)
    }
}