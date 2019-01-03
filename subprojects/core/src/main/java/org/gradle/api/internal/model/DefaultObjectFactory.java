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
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.provider.DefaultListProperty;
import org.gradle.api.internal.provider.DefaultMapProperty;
import org.gradle.api.internal.provider.DefaultPropertyState;
import org.gradle.api.internal.provider.DefaultSetProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultObjectFactory implements ObjectFactory {
    private final Instantiator instantiator;
    private final NamedObjectInstantiator namedObjectInstantiator;
    private final FileResolver fileResolver;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FilePropertyFactory filePropertyFactory;

    public DefaultObjectFactory(Instantiator instantiator, NamedObjectInstantiator namedObjectInstantiator, FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory, FilePropertyFactory filePropertyFactory) {
        this.instantiator = instantiator;
        this.namedObjectInstantiator = namedObjectInstantiator;
        this.fileResolver = fileResolver;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.filePropertyFactory = filePropertyFactory;
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
    public DirectoryProperty directoryProperty() {
        return filePropertyFactory.newDirectoryProperty();
    }

    @Override
    public RegularFileProperty fileProperty() {
        return filePropertyFactory.newFileProperty();
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
        if (List.class.isAssignableFrom(valueType)) {
            DeprecationLogger.nagUserOfReplacedMethodInvocation("ObjectFactory.property() to create a property of type List<T>", "ObjectFactory.listProperty()");
        } else if (Set.class.isAssignableFrom(valueType)) {
            DeprecationLogger.nagUserOfReplacedMethodInvocation("ObjectFactory.property() method to create a property of type Set<T>", "ObjectFactory.setProperty()");
        } else if (Map.class.isAssignableFrom(valueType)) {
            DeprecationLogger.nagUserOfReplacedMethodInvocation("ObjectFactory.property() method to create a property of type Map<K, V>", "ObjectFactory.mapProperty()");
        } else if (Directory.class.isAssignableFrom(valueType)) {
            DeprecationLogger.nagUserOfReplacedMethodInvocation("ObjectFactory.property() method to create a property of type Directory", "ObjectFactory.directoryProperty()");
        } else if (RegularFile.class.isAssignableFrom(valueType)) {
            DeprecationLogger.nagUserOfReplacedMethodInvocation("ObjectFactory.property() method to create a property of type RegularFile", "ObjectFactory.fileProperty()");
        }

        return new DefaultPropertyState<T>(valueType);
    }

    @Override
    public <T> ListProperty<T> listProperty(Class<T> elementType) {
        if (elementType.isPrimitive()) {
            // Kotlin passes these types for its own basic types
            return Cast.uncheckedCast(listProperty(JavaReflectionUtil.getWrapperTypeForPrimitiveType(elementType)));
        }
        return new DefaultListProperty<T>(elementType);
    }

    @Override
    public <T> SetProperty<T> setProperty(Class<T> elementType) {
        if (elementType.isPrimitive()) {
            // Kotlin passes these types for its own basic types
            return Cast.uncheckedCast(setProperty(JavaReflectionUtil.getWrapperTypeForPrimitiveType(elementType)));
        }
        return new DefaultSetProperty<T>(elementType);
    }

    @Override
    public <K, V> MapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType) {
        if (keyType.isPrimitive()) {
            // Kotlin passes these types for its own basic types
            return Cast.uncheckedCast(mapProperty(JavaReflectionUtil.getWrapperTypeForPrimitiveType(keyType), valueType));
        }
        if (valueType.isPrimitive()) {
            // Kotlin passes these types for its own basic types
            return Cast.uncheckedCast(mapProperty(keyType, JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueType)));
        }
        return new DefaultMapProperty<K, V>(keyType, valueType);
    }
}
