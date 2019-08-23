/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLookup;

/**
 * A helper used by generated classes to create managed instances.
 */
public class ManagedObjectFactory {
    private final ServiceLookup serviceLookup;
    private final Instantiator instantiator;

    public ManagedObjectFactory(ServiceLookup serviceLookup, Instantiator instantiator) {
        this.serviceLookup = serviceLookup;
        this.instantiator = instantiator;
    }

    public Object newInstance(Class<?> type) {
        if (type.isAssignableFrom(ConfigurableFileCollection.class)) {
            return getObjectFactory().fileCollection();
        }
        if (type.isAssignableFrom(DirectoryProperty.class)) {
            return getObjectFactory().directoryProperty();
        }
        if (type.isAssignableFrom(RegularFileProperty.class)) {
            return getObjectFactory().fileProperty();
        }
        return instantiator.newInstance(type);
    }

    public Object newInstance(Class<?> type, Class<?> paramType) {
        if (type.isAssignableFrom(Property.class)) {
            return getObjectFactory().property(paramType);
        }
        if (type.isAssignableFrom(ListProperty.class)) {
            return getObjectFactory().listProperty(paramType);
        }
        if (type.isAssignableFrom(SetProperty.class)) {
            return getObjectFactory().setProperty(paramType);
        }
        throw new IllegalArgumentException("Don't know how to create an instance of type " + type.getName());
    }

    public Object newInstance(Class<?> type, Class<?> keyType, Class<?> valueType) {
        if (type.isAssignableFrom(MapProperty.class)) {
            return getObjectFactory().mapProperty(keyType, valueType);
        }
        throw new IllegalArgumentException("Don't know how to create an instance of type " + type.getName());
    }

    private ObjectFactory getObjectFactory() {
        return (ObjectFactory) serviceLookup.get(ObjectFactory.class);
    }
}
