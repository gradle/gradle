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

package org.gradle.api.internal.model;

import org.gradle.api.Named;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.provider.DefaultListProperty;
import org.gradle.api.internal.provider.DefaultPropertyState;
import org.gradle.api.internal.provider.DefaultSetProperty;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;

public class DefaultObjectFactory implements ObjectFactory {
    private final Instantiator instantiator;
    private final NamedObjectInstantiator namedObjectInstantiator;
    private final FileResolver fileResolver;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public DefaultObjectFactory(Instantiator instantiator, NamedObjectInstantiator namedObjectInstantiator, FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.instantiator = instantiator;
        this.namedObjectInstantiator = namedObjectInstantiator;
        this.fileResolver = fileResolver;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    @Override
    public <T extends Named> T named(final Class<T> type, final String name) {
        return namedObjectInstantiator.named(type, name);
    }

    @Override
    public <T> T newInstance(Class<? extends T> type, Object... parameters) throws ObjectInstantiationException {
        return instantiator.newInstance(type, parameters);
    }

    @Override
    public SourceDirectorySet sourceDirectorySet(final String name, final String displayName) {
        return DeprecationLogger.whileDisabled(new Factory<SourceDirectorySet>() {
            @Nullable
            @Override
            public SourceDirectorySet create() {
                return new DefaultSourceDirectorySet(name, displayName, fileResolver, directoryFileTreeFactory, DefaultObjectFactory.this);
            }
        });
    }

    @Override
    public <T> Property<T> property(Class<T> valueType) {
        if (valueType == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        if (valueType.isPrimitive()) {
            // Kotlin passes these types for its own basic types
            return Cast.uncheckedCast(property(JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueType)));
        }

        Property<T> property = new DefaultPropertyState<T>(valueType);

        if (valueType == Boolean.class) {
            ((Property<Boolean>) property).set(Providers.FALSE);
        } else if (valueType == Byte.class) {
            ((Property<Byte>) property).set(Providers.BYTE_ZERO);
        } else if (valueType == Short.class) {
            ((Property<Short>) property).set(Providers.SHORT_ZERO);
        } else if (valueType == Integer.class) {
            ((Property<Integer>) property).set(Providers.INTEGER_ZERO);
        } else if (valueType == Long.class) {
            ((Property<Long>) property).set(Providers.LONG_ZERO);
        } else if (valueType == Float.class) {
            ((Property<Float>) property).set(Providers.FLOAT_ZERO);
        } else if (valueType == Double.class) {
            ((Property<Double>) property).set(Providers.DOUBLE_ZERO);
        } else if (valueType == Character.class) {
            ((Property<Character>) property).set(Providers.CHAR_ZERO);
        }

        return property;
    }

    @Override
    public <T> ListProperty<T> listProperty(Class<T> elementType) {
        return new DefaultListProperty<T>(elementType);
    }

    @Override
    public <T> SetProperty<T> setProperty(Class<T> elementType) {
        return new DefaultSetProperty<T>(elementType);
    }
}
