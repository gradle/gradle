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
    private final AutoCreateDomainObjectContainer delegate;
    private final ThreadLocal<Boolean> configuring = new ThreadLocal<Boolean>()

    public AutoCreateDomainObjectContainerDelegate(Object owner, AutoCreateDomainObjectContainer delegate) {
        this.delegate = delegate;
        this.owner = owner;
    }

    public Object invokeMethod(String name, Object params) {
        boolean isTopLevelCall = !configuring.get()
        configuring.set(true)
        try {
            groovy.lang.MissingMethodException failure
            try {
                return delegate.invokeMethod(name, params)
            } catch (groovy.lang.MissingMethodException e) {
                failure = e
            }

            // try the owner
            try {
                owner.invokeMethod(name, params)
            } catch (groovy.lang.MissingMethodException e) {
                // ignore
            }

            boolean isConfigureMethod = params.length == 1 && params[0] instanceof Closure
            boolean failureIsThis = failure instanceof MissingMethodException && failure.target == delegate.asDynamicObject && failure.method == name
            if (!isTopLevelCall || !isConfigureMethod || !failureIsThis) {
                throw failure
            }

            // looks like a configure method - try the delegate again
            delegate.add(name)
            return delegate.invokeMethod(name, params);
        } finally {
            configuring.set(!isTopLevelCall)
        }
    }

    public Object get(String name) {
        try {
            return delegate."$name"
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        // try the owner
        try {
            return owner."$name"
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        // try the delegate again
        delegate.add(name)
        return delegate."$name"
    }
}