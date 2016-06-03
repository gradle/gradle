/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.metaobject;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import org.gradle.api.internal.DynamicObjectUtil;

public class ConfigureDelegate extends GroovyObjectSupport {
    protected final DynamicObject _owner;
    protected final DynamicObject _delegate;
    private final ThreadLocal<Boolean> _configuring = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public ConfigureDelegate(Closure configureClosure, Object delegate) {
        _owner = DynamicObjectUtil.asDynamicObject(configureClosure.getOwner());
        _delegate = DynamicObjectUtil.asDynamicObject(delegate);
    }

    @Override
    public String toString() {
        return _delegate.toString();
    }

    protected void _configure(String name, Object[] params, InvokeMethodResult result) {
    }

    protected void _configure(String name, GetPropertyResult result) {
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

            MissingMethodException failure = null;
            if (!isAlreadyConfiguring) {
                // Try to configure element
                try {
                    _configure(name, params, result);
                } catch (MissingMethodException e) {
                    // Workaround for backwards compatibility. Previously, this case would unintentionally cause the method to be invoked on the owner
                    // continue below
                    failure = e;
                }
                if (result.isFound()) {
                    return result.getResult();
                }
            }

            // try the owner
            _owner.invokeMethod(name, result, params);
            if (result.isFound()) {
                return result.getResult();
            }

            if (failure != null) {
                throw failure;
            }

            throw _delegate.methodMissingException(name, params);
        } finally {
            _configuring.set(isAlreadyConfiguring);
        }
    }

    @Override
    public void setProperty(String property, Object newValue) {
        SetPropertyResult result = new SetPropertyResult();
        _delegate.setProperty(property, newValue, result);
        if (result.isFound()) {
            return;
        }

        _owner.setProperty(property, newValue, result);
        if (result.isFound()) {
            return;
        }

        throw _delegate.setMissingProperty(property);
    }

    public Object getProperty(String name) {
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

            if (!isAlreadyConfiguring) {
                // Try to configure an element
                _configure(name, result);
                if (result.isFound()) {
                    return result.getValue();
                }
            }

            throw _delegate.getMissingProperty(name);
        } finally {
            _configuring.set(isAlreadyConfiguring);
        }
    }
}
