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
import org.codehaus.groovy.runtime.metaclass.MissingMethodExecutionFailed;
import org.gradle.internal.metaobject.DynamicInvokeResult.AdditionalContext;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

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
    public DynamicInvokeResult tryGetProperty(String name) {
        return propertyNotFound(name);
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
        DynamicInvokeResult result = tryGetProperty(name);
        if (!result.isFound()) {
            throw getMissingProperty(result, name);
        }
        return result.getValue();
    }

    @Override
    public DynamicInvokeResult trySetProperty(String name, Object value) {
        return propertyNotFound(name);
    }

    @Override
    public void setProperty(String name, Object value) throws MissingPropertyException {
        DynamicInvokeResult result = trySetProperty(name, value);
        if (!result.isFound()) {
            throw setMissingProperty(result, name);
        }
    }

    @Override
    public MissingPropertyException getMissingProperty(DynamicInvokeResult result, String name) {
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

    @Override
    public MissingPropertyException setMissingProperty(DynamicInvokeResult result, String name) {
        StringBuilder message = new StringBuilder();
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        if (publicType != null && includeDisplayName) {
            message.append(String.format("Could not set unknown property '%s' for %s of type %s.", name, getDisplayName(), publicType.getName()));
        } else if (publicType != null) {
            message.append(String.format("Could not set unknown property '%s' for object of type %s.", name, publicType.getName()));
        } else {
            // Use the display name anyway
            message.append(String.format("Could not set unknown property '%s' for %s.", name, getDisplayName()));
        }

        if (DynamicInvokeResult.hasAdditionalContext()) {
            DynamicInvokeResult.getAdditionalContext().stream()
                    .map(DynamicInvokeResult.AdditionalContext::getMessage)
                    .map("\n\t"::concat)
                    .forEach(message::append);
        }

        return new MissingPropertyException(message.toString(), name, publicType);
    }

    protected GroovyRuntimeException setReadOnlyProperty(DynamicInvokeResult result, String name) {
        StringBuilder message = new StringBuilder();
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        if (publicType != null && includeDisplayName) {
            message.append(String.format("Cannot set the value of read-only property '%s' for %s of type %s.", name, getDisplayName(), publicType.getName()));
        } else if (publicType != null) {
            message.append(String.format("Cannot set the value of read-only property '%s' for object of type %s.", name, publicType.getName()));
        } else {
            // Use the display name anyway
            message.append(String.format("Cannot set the value of read-only property '%s' for %s.", name, getDisplayName()));
        }

        if (DynamicInvokeResult.hasAdditionalContext()) {
            DynamicInvokeResult.getAdditionalContext().stream()
                    .map(DynamicInvokeResult.AdditionalContext::getMessage)
                    .map("\n\t"::concat)
                    .forEach(message::append);
        }

        throw new GroovyRuntimeException(message.toString());
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
    public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
        if (this instanceof ProvidesMissingMethodContext) {
            Optional<AdditionalContext> context = ((ProvidesMissingMethodContext) this).getAdditionalContext(name, arguments);
            return DynamicInvokeResult.notFound(context.orElse(null));
        } else {
            return methodNotFound(name, arguments);
        }
    }

    @Override
    public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
        DynamicInvokeResult result = tryInvokeMethod(name, arguments);
        if (result.isFound()) {
            return result.getValue();
        }
        throw methodMissingException(result, name, arguments);
    }

    public MissingMethodException methodMissingException(DynamicInvokeResult result, String name, Object... params) {
        Class<?> publicType = getPublicType();
        boolean includeDisplayName = hasUsefulDisplayName();
        final StringBuilder message = new StringBuilder();
        if (publicType != null && includeDisplayName) {
            message.append(String.format("Could not find method %s() for arguments %s on %s of type %s.", name, Arrays.toString(params), getDisplayName(), publicType.getName()));
        } else if (publicType != null) {
            message.append(String.format("Could not find method %s() for arguments %s on object of type %s.", name, Arrays.toString(params), publicType.getName()));
        } else {
            // Include the display name anyway
            message.append(String.format("Could not find method %s() for arguments %s on %s.", name, Arrays.toString(params), getDisplayName()));
        }
        if (DynamicInvokeResult.hasAdditionalContext()) {
            DynamicInvokeResult.getAdditionalContext().stream()
                    .map(DynamicInvokeResult.AdditionalContext::getMessage)
                    .map("\n\t"::concat)
                    .forEach(message::append);
        }

        // https://github.com/apache/groovy/commit/75c068207ba24648ea2d698c520601c6fcf0a45b
        return new CustomMissingMethodExecutionFailed(name, publicType, message.toString(), params);
    }

    private static class CustomMissingMethodExecutionFailed extends MissingMethodExecutionFailed {

        public CustomMissingMethodExecutionFailed(String name, Class<?> publicType, String message, Object... params) {
            super(name, publicType, params, false, new CustomMessageMissingMethodException(name, publicType, message, params));
        }

        @Override
        public String getMessage() {
            return getCause().getMessage();
        }
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
