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

import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * An empty {@link DynamicObject}.
 */
public abstract class AbstractDynamicObject implements DynamicObject {
    public abstract String getDisplayName();

    @Override
    public String toString() {
        return "DynamicObject for " + getDisplayName();
    }

    @Override
    public boolean hasProperty(String name) {
        return false;
    }

    @Override
    public void getProperty(String name, GetPropertyResult result) {
        // No such property
    }

    @Nullable
    public Class<?> getPublicType() {
        return null;
    }

    public boolean hasUsefulDisplayName() {
        return true;
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

    public MissingPropertyException getMissingProperty(String name) {
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        if (publicType != null && includeDisplayName) {
            return new MissingPropertyException(String.format("Could not get unknown property '%s' for %s of type %s.", name,
                    getDisplayName(), publicType.getName()), name, publicType);
        } else if (publicType != null) {
            return new MissingPropertyException(String.format("Could not get unknown property '%s' for object of type %s.", name,
                    publicType.getName()), name, publicType);
        } else {
            // Use the display name anyway
            return new MissingPropertyException(String.format("Could not get unknown property '%s' for %s.", name,
                    getDisplayName()), name, null);
        }
    }

    protected GroovyRuntimeException getWriteOnlyProperty(String name) {
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        if (publicType != null && includeDisplayName) {
            return new GroovyRuntimeException(String.format(
                    "Cannot get the value of write-only property '%s' for %s of type %s.", name, getDisplayName(), publicType.getName()));
        } else if (publicType != null) {
            return new GroovyRuntimeException(String.format(
                    "Cannot get the value of write-only property '%s' for object of type %s.", name, publicType.getName()));
        } else {
            // Use the display name anyway
            return new GroovyRuntimeException(String.format(
                    "Cannot get the value of write-only property '%s' for %s.", name, getDisplayName()));
        }
    }

    public MissingPropertyException setMissingProperty(String name) {
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        if (publicType != null && includeDisplayName) {
            return new MissingPropertyException(String.format("Could not set unknown property '%s' for %s of type %s.", name,
                    getDisplayName(), publicType.getName()), name, publicType);
        } else if (publicType != null) {
            return new MissingPropertyException(String.format("Could not set unknown property '%s' for object of type %s.", name,
                    publicType.getName()), name, publicType);
        } else {
            // Use the display name anyway
            return new MissingPropertyException(String.format("Could not set unknown property '%s' for %s.", name,
                    getDisplayName()), name, null);
        }
    }

    protected GroovyRuntimeException setReadOnlyProperty(String name) {
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        if (publicType != null && includeDisplayName) {
            return new GroovyRuntimeException(String.format(
                    "Cannot set the value of read-only property '%s' for %s of type %s.", name, getDisplayName(), publicType.getName()));
        } else if (publicType != null) {
            return new GroovyRuntimeException(String.format(
                    "Cannot set the value of read-only property '%s' for object of type %s.", name, publicType.getName()));
        } else {
            // Use the display name anyway
            return new GroovyRuntimeException(String.format(
                    "Cannot set the value of read-only property '%s' for %s.", name, getDisplayName()));
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
    public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
        // No methods
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
        InvokeMethodResult result = new InvokeMethodResult();
        invokeMethod(name, result, arguments);
        if (result.isFound()) {
            return result.getResult();
        }
        throw methodMissingException(name, arguments);
    }

    public MissingMethodException methodMissingException(String name, Object... params) {
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        final String message;
        if (publicType != null && includeDisplayName) {
            message = String.format("Could not find method %s() for arguments %s on %s of type %s.", name, Arrays.toString(params), getDisplayName(), publicType.getName());
        } else if (publicType != null) {
            message = String.format("Could not find method %s() for arguments %s on object of type %s.", name, Arrays.toString(params), publicType.getName());
        } else {
            // Include the display name anyway
            message = String.format("Could not find method %s() for arguments %s on %s.", name, Arrays.toString(params), getDisplayName());
        }
        return new CustomMessageMissingMethodException(name, publicType, message, params);
    }

    private static class CustomMessageMissingMethodException extends MissingMethodException {
        private final String message;

        CustomMessageMissingMethodException(String name, Class<?> publicType, String message, Object... params) {
            super(name, publicType, params);
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
