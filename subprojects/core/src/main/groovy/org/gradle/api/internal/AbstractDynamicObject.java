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

import groovy.lang.*;

import java.util.Map;
import java.util.Collections;
import java.util.Arrays;

/**
 * An empty {@link DynamicObject}.
 */
public abstract class AbstractDynamicObject implements DynamicObject {
    protected abstract String getDisplayName();

    public boolean hasProperty(String name) {
        return false;
    }

    public Object getProperty(String name) throws MissingPropertyException {
        throw propertyMissingException(name);
    }

    public void setProperty(String name, Object value) throws MissingPropertyException {
        throw propertyMissingException(name);
    }

    protected MissingPropertyException propertyMissingException(String name) {
        throw new MissingPropertyException(String.format("Could not find property '%s' on %s.", name,
                getDisplayName()), name, null);
    }

    public Map<String, ?> getProperties() {
        return Collections.emptyMap();
    }

    public boolean hasMethod(String name, Object... arguments) {
        return false;
    }

    public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
        throw methodMissingException(name, arguments);
    }

    public boolean isMayImplementMissingMethods() {
        return false;
    }

    public boolean isMayImplementMissingProperties() {
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

