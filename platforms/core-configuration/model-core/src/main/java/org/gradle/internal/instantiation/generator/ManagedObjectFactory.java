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
import org.gradle.internal.DisplayName;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.OwnerAware;

/**
 * A helper used by generated classes to create managed instances.
 */
public class ManagedObjectFactory {
    private final ServiceLookup serviceLookup;
    private final InstanceGenerator instantiator;
    private final PropertyRoleAnnotationHandler roleHandler;

    public ManagedObjectFactory(ServiceLookup serviceLookup, InstanceGenerator instantiator, PropertyRoleAnnotationHandler roleHandler) {
        this.serviceLookup = serviceLookup;
        this.instantiator = instantiator;
        this.roleHandler = roleHandler;
    }

    // Also called from generated code
    public static <T> T attachOwner(T instance, ModelObject owner, String propertyName) {
        if (instance instanceof OwnerAware) {
            ((OwnerAware) instance).attachOwner(owner, displayNameFor(owner, propertyName));
        }
        return instance;
    }

    // Called from generated code
    public void applyRole(Object value, ModelObject owner) {
        roleHandler.applyRoleTo(owner, value);
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type) {
        Object providedType = getManagedObjectProvider().newInstance(type);
        if (providedType != null) {
            return attachOwner(providedType, owner, propertyName);
        }
        return attachOwner(instantiator.newInstanceWithDisplayName(type, displayNameFor(owner, propertyName)), owner, propertyName);
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type, Class<?> paramType) {
        Object providedType = getManagedObjectProvider().newInstance(type, paramType);
        if (providedType != null) {
            return attachOwner(providedType, owner, propertyName);
        }
        throw new IllegalArgumentException("Unable to create an instance of type " + type.getName());
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type, Class<?> keyType, Class<?> valueType) {
        Object providedType = getManagedObjectProvider().newInstance(type, keyType, valueType);
        if (providedType != null) {
            return attachOwner(providedType, owner, propertyName);
        }
        throw new IllegalArgumentException("Unable to create an instance of type " + type.getName());
    }

    private static ManagedPropertyName displayNameFor(ModelObject owner, String propertyName) {
        if (owner.getModelIdentityDisplayName() instanceof ManagedPropertyName) {
            ManagedPropertyName root = (ManagedPropertyName) owner.getModelIdentityDisplayName();
            return new ManagedPropertyName(root.ownerDisplayName, root.propertyName + "." + propertyName);
        } else {
            return new ManagedPropertyName(cachedOwnerDisplayNameOf(owner), propertyName);
        }
    }

    private static Cached<String> cachedOwnerDisplayNameOf(ModelObject owner) {
        return Cached.of(() -> {
            Describable ownerModelIdentityDisplayName = owner.getModelIdentityDisplayName();
            if (ownerModelIdentityDisplayName != null) {
                return ownerModelIdentityDisplayName.getDisplayName();
            }
            return null;
        });
    }

    private ManagedObjectRegistry getManagedObjectProvider() {
        ManagedObjectRegistry managedObjectRegistry = (ManagedObjectRegistry) serviceLookup.find(ManagedObjectRegistry.class);
        if (managedObjectRegistry == null) {
            throw new IllegalStateException("No managed object registry found");
        }
        return managedObjectRegistry;
    }

    private static class ManagedPropertyName implements DisplayName {
        private final Cached<String> ownerDisplayName;
        private final String propertyName;

        public ManagedPropertyName(Cached<String> ownerDisplayName, String propertyName) {
            this.ownerDisplayName = ownerDisplayName;
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
            if (ownerDisplayName.get() != null) {
                return ownerDisplayName.get() + " property '" + propertyName + "'";
            } else {
                return "property '" + propertyName + "'";
            }
        }
    }
}
