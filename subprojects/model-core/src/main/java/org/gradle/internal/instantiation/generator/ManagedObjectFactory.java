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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.DisplayName;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.OwnerAware;

/**
 * A helper used by generated classes to create managed instances.
 */
public class ManagedObjectFactory {
    private final ServiceLookup serviceLookup;
    private final InstanceGenerator instantiator;

    public ManagedObjectFactory(ServiceLookup serviceLookup, InstanceGenerator instantiator) {
        this.serviceLookup = serviceLookup;
        this.instantiator = instantiator;
    }

    // Also called from generated code
    public static <T> T attachOwner(ModelObject owner, String propertyName, T instance) {
        if (instance instanceof OwnerAware) {
            ((OwnerAware) instance).attachOwner(owner, displayNameFor(owner, propertyName));
        }
        return instance;
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type) {
        if (type.isAssignableFrom(ConfigurableFileCollection.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().fileCollection());
        }
        if (type.isAssignableFrom(ConfigurableFileTree.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().fileTree());
        }
        if (type.isAssignableFrom(DirectoryProperty.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().directoryProperty());
        }
        if (type.isAssignableFrom(RegularFileProperty.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().fileProperty());
        }
        return instantiator.newInstanceWithDisplayName(type, displayNameFor(owner, propertyName));
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type, Class<?> paramType) {
        if (type.isAssignableFrom(Property.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().property(paramType));
        }
        if (type.isAssignableFrom(ListProperty.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().listProperty(paramType));
        }
        if (type.isAssignableFrom(SetProperty.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().setProperty(paramType));
        }
        if (type.isAssignableFrom(NamedDomainObjectContainer.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().domainObjectContainer(paramType));
        }
        if (type.isAssignableFrom(DomainObjectSet.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().domainObjectSet(paramType));
        }
        throw new IllegalArgumentException("Don't know how to create an instance of type " + type.getName());
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type, Class<?> keyType, Class<?> valueType) {
        if (type.isAssignableFrom(MapProperty.class)) {
            return attachOwner(owner, propertyName, getObjectFactory().mapProperty(keyType, valueType));
        }
        throw new IllegalArgumentException("Don't know how to create an instance of type " + type.getName());
    }

    private static ManagedPropertyName displayNameFor(ModelObject owner, String propertyName) {
        if (owner.getIdentityDisplayName() instanceof ManagedPropertyName) {
            ManagedPropertyName root = (ManagedPropertyName) owner.getIdentityDisplayName();
            return new ManagedPropertyName(root.owner, root.propertyName + "." + propertyName);
        } else {
            return new ManagedPropertyName(owner, propertyName);
        }
    }

    private ObjectFactory getObjectFactory() {
        return (ObjectFactory) serviceLookup.get(ObjectFactory.class);
    }

    private static class ManagedPropertyName implements DisplayName {
        private final ModelObject owner;
        private final String propertyName;

        public ManagedPropertyName(ModelObject owner, String propertyName) {
            this.owner = owner;
            this.propertyName = propertyName;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        public String getCapitalizedDisplayName() {
            return StringUtils.capitalize(getDisplayName());
        }

        @Override
        public String getDisplayName() {
            Describable ownerDisplayName = owner.getIdentityDisplayName();
            if (ownerDisplayName != null) {
                return ownerDisplayName.getDisplayName() + " property '" + propertyName + "'";
            } else {
                return "property '" + propertyName + "'";
            }
        }
    }
}
