/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.project;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class MutableStateAccessAwareDynamicObject extends AbstractDynamicObject {

    private final AbstractDynamicObject delegate;
    private final Runnable onMutableStateAccess;

    public MutableStateAccessAwareDynamicObject(AbstractDynamicObject delegate, Runnable onMutableStateAccess) {
        this.delegate = delegate;
        this.onMutableStateAccess = onMutableStateAccess;
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public boolean hasProperty(String name) {
        onMutableStateAccess.run();
        return delegate.hasProperty(name);
    }

    @Override
    public DynamicInvokeResult tryGetProperty(String name) {
        DynamicInvokeResult result = delegate.tryGetProperty(name);
        if (!result.isFound()) {
            onMutableStateAccess.run();
        }
        return result;
    }

    @Nullable
    @Override
    public Class<?> getPublicType() {
        return delegate.getPublicType();
    }

    @Override
    public boolean hasUsefulDisplayName() {
        return delegate.hasUsefulDisplayName();
    }

    @Override
    public Object getProperty(String name) throws MissingPropertyException {
        return delegate.getProperty(name);
    }

    @Override
    public DynamicInvokeResult trySetProperty(String name, @Nullable Object value) {
        DynamicInvokeResult result = delegate.trySetProperty(name, value);
        if (!result.isFound()) {
            onMutableStateAccess.run();
        }
        return result;
    }

    @Override
    public void setProperty(String name, @Nullable Object value) throws MissingPropertyException {
        delegate.setProperty(name, value);
    }

    @Override
    public MissingPropertyException getMissingProperty(String name) {
        return delegate.getMissingProperty(name);
    }

    @Override
    public MissingPropertyException setMissingProperty(String name) {
        return delegate.setMissingProperty(name);
    }

    @Override
    public Map<String, ?> getProperties() {
        onMutableStateAccess.run();
        return delegate.getProperties();
    }

    @Override
    public boolean hasMethod(String name, @Nullable Object... arguments) {
        onMutableStateAccess.run();
        return delegate.hasMethod(name, arguments);
    }

    @Override
    public DynamicInvokeResult tryInvokeMethod(String name, @Nullable Object... arguments) {
        DynamicInvokeResult result = delegate.tryInvokeMethod(name, arguments);
        if (!result.isFound()) {
            onMutableStateAccess.run();
        }
        return result;
    }

    @Override
    public Object invokeMethod(String name, @Nullable Object... arguments) throws MissingMethodException {
        return delegate.invokeMethod(name, arguments);
    }

    @Override
    public MissingMethodException methodMissingException(String name, @Nullable Object... params) {
        return delegate.methodMissingException(name, params);
    }
}
