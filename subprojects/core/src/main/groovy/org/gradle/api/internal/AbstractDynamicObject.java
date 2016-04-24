/*
 * Copyright 2008 the original author or authors.
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

import groovy.lang.MissingPropertyException;
import org.gradle.api.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * An empty {@link DynamicObject}.
 */
public abstract class AbstractDynamicObject implements DynamicObject {
    protected abstract String getDisplayName();

    @Override
    public boolean hasProperty(String name) {
        return false;
    }

    @Override
    public void getProperty(String name, GetPropertyResult result) {
        // No such property
    }

    @Nullable
    protected Class<?> getPublicType() {
        return null;
    }

    @Override
    public Object getProperty(String name) throws MissingPropertyException {
        GetPropertyResult result = new GetPropertyResult();
        getProperty(name, result);
        if (!result.isFound()) {
            throw getMissingProperty(name);
        }
        return result.getValue();
    }

    @Override
    public void setProperty(String name, Object value, SetPropertyResult result) {
        // No such property
    }

    @Override
    public void setProperty(String name, Object value) throws MissingPropertyException {
        SetPropertyResult result = new SetPropertyResult();
        setProperty(name, value, result);
        if (!result.isFound()) {
            throw setMissingProperty(name);
        }
    }

    protected MissingPropertyException getMissingProperty(String name) {
        Class<?> publicType = getPublicType();
        if (publicType != null) {
            return new MissingPropertyException(String.format("Could not get unknown property '%s' for %s of type %s.", name,
                    getDisplayName(), publicType.getName()), name, publicType);
        } else {
            return new MissingPropertyException(String.format("Could not get unknown property '%s' for %s.", name,
                    getDisplayName()), name, null);
        }
    }

    protected MissingPropertyException setMissingProperty(String name) {
        Class<?> publicType = getPublicType();
        if (publicType != null) {
            return new MissingPropertyException(String.format("Could not set unknown property '%s' for %s of type %s.", name,
                    getDisplayName(), publicType.getName()), name, publicType);
        } else {
            return new MissingPropertyException(String.format("Could not set unknown property '%s' for %s.", name,
                    getDisplayName()), name, null);
        }
    }

    @Override
    public Map<String, ?> getProperties() {
        return Collections.emptyMap();
    }

    @Override
    public boolean hasMethod(String name, Object... arguments) {
        return false;
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
        throw methodMissingException(name, arguments);
    }

    @Override
    public boolean isMayImplementMissingMethods() {
        return false;
    }

    protected groovy.lang.MissingMethodException methodMissingException(String name, Object... params) {
        return new MissingMethodException(this, getDisplayName(), name, params);
    }
}

class MissingMethodException extends groovy.lang.MissingMethodException {
    private final DynamicObject target;
    private final String displayName;

    public MissingMethodException(DynamicObject target, String displayName, String name, Object... arguments) {
        super(name, null, arguments);
        this.target = target;
        this.displayName = displayName;
    }

    public DynamicObject getTarget() {
        return target;
    }

    public String getMessage() {
        return String.format("Could not find method %s() for arguments %s on %s.", getMethod(), Arrays.toString(
                getArguments()), displayName);
    }
}

