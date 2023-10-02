/*
 * Copyright 2018 the original author or authors.
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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

@SuppressWarnings({"FieldNamingConvention", "NewMethodNamingConvention"})
@NotThreadSafe
public class ConfigureDelegate extends GroovyObjectSupport {
    protected final DynamicObject _owner;
    protected final DynamicObject _delegate;
    private final Object _original_owner;
    private boolean _configuring;

    public ConfigureDelegate(Closure<?> configureClosure, Object delegate) {
        _original_owner = configureClosure.getOwner();
        _owner = DynamicObjectUtil.asDynamicObject(_original_owner);
        _delegate = DynamicObjectUtil.asDynamicObject(delegate);
    }

    @Override
    public String toString() {
        return _delegate.toString();
    }

    @Nullable
    public Object _original_owner() {
        return _original_owner;
    }

    protected DynamicInvokeResult _configure(String name, Object[] params) {
        return DynamicInvokeResult.notFound();
    }

    protected DynamicInvokeResult _configure(String name) {
        return DynamicInvokeResult.notFound();
    }

    @Override
    public Object invokeMethod(String name, Object paramsObj) {
        Object[] params = (Object[])paramsObj;

        boolean isAlreadyConfiguring = _configuring;
        _configuring = true;
        try {
            DynamicInvokeResult result = _delegate.tryInvokeMethod(name, params);
            if (result.isFound()) {
                return result.getValue();
            }

            if (!isAlreadyConfiguring) {
                // Try to configure element
                result = _configure(name, params);
                if (result.isFound()) {
                    return result.getValue();
                }
            }

            // try the owner
            result = _owner.tryInvokeMethod(name, params);
            if (result.isFound()) {
                return result.getValue();
            }

            throw _delegate.methodMissingException(name, params);
        } finally {
            _configuring = isAlreadyConfiguring;
        }
    }

    @Override
    public void setProperty(String property, Object newValue) {
        DynamicInvokeResult result = _delegate.trySetProperty(property, newValue);
        if (result.isFound()) {
            return;
        }

        result = _owner.trySetProperty(property, newValue);
        if (result.isFound()) {
            return;
        }

        throw _delegate.setMissingProperty(property);
    }

    @Override
    public Object getProperty(String name) {
        boolean isAlreadyConfiguring = _configuring;
        _configuring = true;
        try {
            DynamicInvokeResult result = _delegate.tryGetProperty(name);
            if (result.isFound()) {
                return result.getValue();
            }

            result = _owner.tryGetProperty(name);
            if (result.isFound()) {
                return result.getValue();
            }

            if (!isAlreadyConfiguring) {
                // Try to configure an element
                result = _configure(name);
                if (result.isFound()) {
                    return result.getValue();
                }
            }

            throw _delegate.getMissingProperty(name);
        } finally {
            _configuring = isAlreadyConfiguring;
        }
    }
}
