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
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Action;

public class ConfigureDelegate extends GroovyObjectSupport {
    private final DynamicObject owner;
    private final DynamicObject delegate;
    private final Action<String> onMissing;
    private final ThreadLocal<Boolean> configuring = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public ConfigureDelegate(Object owner, Object delegate) {
        this(owner, delegate, Actions.<String>doNothing());
    }

    public ConfigureDelegate(Object owner, Object delegate, Action<String> onMissing) {
        this.owner = DynamicObjectUtil.asDynamicObject(owner);
        this.delegate = DynamicObjectUtil.asDynamicObject(delegate);
        this.onMissing = onMissing;
    }

    @SuppressWarnings("EmptyCatchBlock")
    public Object invokeMethod(String name, Object paramsObj) {
        Object[] params = (Object[])paramsObj;

        boolean isTopLevelCall = !configuring.get();
        configuring.set(true);
        try {
            if (delegate.hasMethod(name, params)) {
                return delegate.invokeMethod(name, params);
            }

            // try the owner
            try {
                return owner.invokeMethod(name, params);
            } catch (groovy.lang.MissingMethodException e) {
                // ignore
            }

            boolean isConfigureMethod = (params.length == 1) && (params[0] instanceof Closure);
            if (isTopLevelCall && isConfigureMethod) {
                onMissing.execute(name);
            }

            return delegate.invokeMethod(name, params);
        } finally {
            configuring.set(!isTopLevelCall);
        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    public Object get(String name) {
        if (delegate.hasProperty(name)) {
            return delegate.getProperty(name);
        }

        // try the owner
        try {
            return owner.getProperty(name);
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        // try the delegate again
        onMissing.execute(name);
        return delegate.getProperty(name);
    }
}