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

package org.gradle.api.internal.changedetection.state;

import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import org.apache.commons.lang.SerializationUtils;
import org.gradle.api.Nullable;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.util.DeprecationLogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;
import java.lang.reflect.Method;

class DefaultInputProperty extends AbstractInputProperty {
    private transient Object inputProperty;
    private final byte[] serializedInputProperty;

    private DefaultInputProperty(HashCode hashedInputProperty, byte[] serializedInputProperty, Object inputProperty) {
        super(hashedInputProperty);
        this.inputProperty = inputProperty;
        this.serializedInputProperty = serializedInputProperty;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        DefaultInputProperty rhs = (DefaultInputProperty) obj;

        try {
            tryDeserialize(this, rhs);
        } catch (IOException e) {
            // In presence of corruption, both object cannot assert to be equal
            return false;
        } catch (ClassNotFoundException e) {
            // In presence of complete change of property type, both object cannot assert to be equal
            return false;
        }

        boolean isInputPropertyEquals = Objects.equal(inputProperty, rhs.inputProperty);
        Method equalsMethod = getEqualsMethod(inputProperty);
        if (equalsMethod != null && isMethodOverridden(equalsMethod, Object.class)) {
            nagUserOfCustomEqualsInTaskInputPropertyDeprecation(isInputPropertyEquals, Objects.equal(getHashedInputProperty(), rhs.getHashedInputProperty()));
        }
        return isInputPropertyEquals;
    }

    private static void tryDeserialize(DefaultInputProperty lhs, DefaultInputProperty rhs) throws IOException, ClassNotFoundException {
        ClassLoader classLoader = selectClassLoader(lhs, rhs);
        lhs.tryDeserialize(classLoader);
        rhs.tryDeserialize(classLoader);
    }

    private static ClassLoader selectClassLoader(DefaultInputProperty lhs, DefaultInputProperty rhs) {
        if (lhs.inputProperty == null && rhs.inputProperty == null) {
            return DefaultInputProperty.class.getClassLoader();
        } else if (lhs.inputProperty == null) {
            return rhs.inputProperty.getClass().getClassLoader();
        } else {
            return lhs.inputProperty.getClass().getClassLoader();
        }
    }

    private void tryDeserialize(ClassLoader classLoader) throws IOException, ClassNotFoundException {
        if (inputProperty == null && serializedInputProperty != null) {
            inputProperty = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedInputProperty), classLoader).readObject();
        }
    }

    private void nagUserOfCustomEqualsInTaskInputPropertyDeprecation(boolean isObjectEquals, boolean isHashEquals) {
        // In presence of a mismatch between the Object.equals implementation and hashed property, a deprecation warning is shown
        if ((isObjectEquals && !isHashEquals) || (!isObjectEquals && isHashEquals)) {
            DeprecationLogger.nagUserOfDeprecated("Custom equals implementation on task input properties");
        }
    }

    @Nullable
    private static Method getEqualsMethod(Object obj) {
        if (obj == null) {
            return null;
        }
        return getEqualsMethod(obj.getClass());
    }

    @Nullable
    private static Method getEqualsMethod(Class cls) {
        try {
            return cls.getMethod("equals", Object.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean isMethodOverridden(Method method, Class declaringClass) {
        return !method.getDeclaringClass().equals(declaringClass);
    }


    @Override
    public int hashCode() {
        assertInputPropertyDeserialized();
        return Objects.hashCode(inputProperty);
    }

    /**
     * Assert the input property is correctly deserialized before proceeding. Method such as hashCode doesn't have
     * enough information to deserialize the input property. Care must be taken to ensure the input property is
     * deserialized before invoking those methods. Continuing without properly deserializing the input property will
     * return erroneous data.
     */
    private void assertInputPropertyDeserialized() {
        if ((inputProperty == null && serializedInputProperty == null) || (inputProperty != null && serializedInputProperty != null)) {
            throw new RuntimeException("Input property isn't deserialized");
        }
    }

    static DefaultInputProperty create(Object inputProperty) throws NotSerializableException {
        HashCode hash = hash(inputProperty);
        byte[] serializedInputProperty = null;
        if (inputProperty != null) {
            serializedInputProperty = SerializationUtils.serialize((Serializable) inputProperty);
        }

        return new DefaultInputProperty(hash, serializedInputProperty, inputProperty);
    }
}
