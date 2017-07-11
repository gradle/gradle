/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.io.ClassLoaderObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Represents a {@link WorkSpec} that contains constructor parameters.
 */
public class SerializingActionExecutionSpec implements ActionExecutionSpec {
    private final String displayName;
    private final Class<? extends Runnable> implementationClass;
    private final File executionWorkingDir;
    private final byte[] params;

    public SerializingActionExecutionSpec(Class<? extends Runnable> implementationClass, String displayName, File executionWorkingDir, Object[] params) {
        this.implementationClass = implementationClass;
        this.displayName = displayName;
        this.executionWorkingDir = executionWorkingDir;
        this.params = serialize(params);
    }

    @Override
    public Class<? extends Runnable> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public File getExecutionWorkingDir() {
        return executionWorkingDir;
    }

    @Override
    public Object[] getParams(ClassLoader classLoader) {
        return deserialize(classLoader);
    }

    private byte[] serialize(Object[] params) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(params);
        } catch (IOException e) {
            throw new ParameterSerializationException("Could not serialize parameters", e);
        }
        return bos.toByteArray();
    }

    private Object[] deserialize(ClassLoader classLoader) {
        ByteArrayInputStream bis = new ByteArrayInputStream(params);
        try {
            ObjectInputStream ois = new ClassLoaderObjectInputStream(bis, classLoader);
            return (Object[])ois.readObject();
        } catch (IOException e) {
            throw new ParameterSerializationException("Could not deserialize parameters", e);
        } catch (ClassNotFoundException e) {
            throw new ParameterSerializationException("Could not deserialize parameters", e);
        }
    }

    @Contextual
    static class ParameterSerializationException extends RuntimeException {
        ParameterSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
