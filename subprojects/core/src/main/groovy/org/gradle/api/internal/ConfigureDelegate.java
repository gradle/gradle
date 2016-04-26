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

import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;

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

    @Override
    public String toString() {
        return _delegate.toString();
    }

    protected boolean _isConfigureMethod(String name, Object[] params) {
        return false;
    }

    protected Object _configure(String name, Object[] params) {
        // do nothing
        return null;
    }

    @Override
    public Object invokeMethod(String name, Object paramsObj) {
        Object[] params = (Object[])paramsObj;

        boolean isAlreadyConfiguring = _configuring.get();
        _configuring.set(true);
        try {
            InvokeMethodResult result = new InvokeMethodResult();

            _delegate.invokeMethod(name, result, params);
            if (result.isFound()) {
                return result.getResult();
            }

            if (!isAlreadyConfiguring && _isConfigureMethod(name, params)) {
                return _configure(name, params);
            }

            // try the owner
            _owner.invokeMethod(name, result, params);
            if (result.isFound()) {
                return result.getResult();
            }

            throw new MissingMethodException(name, _delegate.getClass(), params);
        } finally {
            _configuring.set(isAlreadyConfiguring);
        }
    }

    public Object get(String name) {
        boolean isAlreadyConfiguring = _configuring.get();
        _configuring.set(true);
        try {
            GetPropertyResult result = new GetPropertyResult();
            _delegate.getProperty(name, result);
            if (result.isFound()) {
                return result.getValue();
            }

            _owner.getProperty(name, result);
            if (result.isFound()) {
                return result.getValue();
            }

            if (isAlreadyConfiguring) {
                throw new MissingPropertyException(name, _delegate.getClass());
            }
            return _configure(name, EMPTY_PARAMS);
        } finally {
            _configuring.set(isAlreadyConfiguring);
        }
    }
}
