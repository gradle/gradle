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

public class ConfigureDelegate extends GroovyObjectSupport {
    private static final Object[] EMPTY_PARAMS = new Object[0];

    protected final DynamicObject _owner;
    protected final DynamicObject _delegate;
    private final ThreadLocal<Boolean> _configuring = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public ConfigureDelegate(Object owner, Object delegate) {
        _owner = DynamicObjectUtil.asDynamicObject(owner);
        _delegate = DynamicObjectUtil.asDynamicObject(delegate);
    }

    protected boolean _isConfigureMethod(String name, Object[] params) {
        return params.length == 1 && params[0] instanceof Closure;
    }

    protected void _configure(String name, Object[] params) {
        // do nothing
    }

    public Object invokeMethod(String name, Object paramsObj) {
        Object[] params = (Object[])paramsObj;

        boolean isTopLevelCall = !_configuring.get();
        _configuring.set(true);
        try {
            if (_delegate.hasMethod(name, params)) {
                return _delegate.invokeMethod(name, params);
            }

            // try the owner
            try {
                return _owner.invokeMethod(name, params);
            } catch (groovy.lang.MissingMethodException e) {
                // ignore
            }

            if (isTopLevelCall && _isConfigureMethod(name, params)) {
                _configure(name, params);
            }

            return _delegate.invokeMethod(name, params);
        } finally {
            _configuring.set(!isTopLevelCall);
        }
    }

    public Object get(String name) {
        if (_delegate.hasProperty(name)) {
            return _delegate.getProperty(name);
        }

        // try the owner
        try {
            return _owner.getProperty(name);
        } catch (groovy.lang.MissingPropertyException e) {
            // Ignore
        }

        _configure(name, EMPTY_PARAMS);

        // try the delegate again
        return _delegate.getProperty(name);
    }
}