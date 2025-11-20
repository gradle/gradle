/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.model.internal.asm.AsmClassGeneratorUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultPropertyFactory implements PropertyFactory {

    private final PropertyHost propertyHost;

    public DefaultPropertyFactory(PropertyHost propertyHost) {
        this.propertyHost = propertyHost;
    }

    @Override
    @Deprecated
    public DefaultProperty<?> propertyWithNoType() {
        return new DefaultProperty<>(propertyHost, null);
    }

    @Override
    @Deprecated
    public <T> DefaultProperty<T> propertyOfAnyType(Class<T> type) {
        return new DefaultProperty<>(propertyHost, maybeAsWrapperType(type));
    }

    @Override
    public <T> DefaultProperty<T> property(Class<T> type) {
        if (List.class.isAssignableFrom(type)) {
            // This is a terrible hack. We made a mistake in making this type a List<Thing> vs using a ListProperty<Thing>
            // Allow this one type to be used with Property until we can fix this elsewhere
            if (!ExternalModuleDependencyBundle.class.isAssignableFrom(type)) {
                throw new InvalidUserCodeException(invalidPropertyCreationError("List<..>", "ListProperty<..>"));
            }
        } else if (Set.class.isAssignableFrom(type)) {
            throw new InvalidUserCodeException(invalidPropertyCreationError("Set<..>", "SetProperty<..>"));
        } else if (Map.class.isAssignableFrom(type)) {
            throw new InvalidUserCodeException(invalidPropertyCreationError("Map<..>", "MapProperty<..>"));
        } else if (Directory.class.isAssignableFrom(type)) {
            throw new InvalidUserCodeException(invalidPropertyCreationError("Directory", "DirectoryProperty"));
        } else if (RegularFile.class.isAssignableFrom(type)) {
            throw new InvalidUserCodeException(invalidPropertyCreationError("RegularFile", "RegularFileProperty"));
        }

        return new DefaultProperty<>(propertyHost, maybeAsWrapperType(type));
    }

    private static String invalidPropertyCreationError(String attemptedType, String correctType) {
        return "Creating a property of type 'Property<" + attemptedType + ">' is unsupported. Use '" + correctType + "' instead.";
    }

    @Override
    public <T> DefaultListProperty<T> listProperty(Class<T> elementType) {
        return new DefaultListProperty<>(propertyHost, maybeAsWrapperType(elementType));
    }

    @Override
    public <T> DefaultSetProperty<T> setProperty(Class<T> elementType) {
        return new DefaultSetProperty<>(propertyHost, maybeAsWrapperType(elementType));
    }

    @Override
    public <V, K> DefaultMapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType) {
        return new DefaultMapProperty<>(propertyHost, maybeAsWrapperType(keyType), maybeAsWrapperType(valueType));
    }

    /**
     * Kotlin passes the Class for the primitive class instead of the boxed
     * class. Convert to the boxed version if necessary.
     */
    private static <T> Class<T> maybeAsWrapperType(Class<T> type) {
        if (type.isPrimitive()) {
            Class<?> wrapper = AsmClassGeneratorUtils.getWrapperTypeForPrimitiveType(type);
            @SuppressWarnings("unchecked")
            Class<T> castWrapper = (Class<T>) wrapper;
            return castWrapper;
        }

        return type;
    }

}
