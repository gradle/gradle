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

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Describable;
import org.gradle.internal.DisplayName;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.OwnerAware;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper used by generated classes to create managed instances.
 */
public class ManagedObjectFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedObjectFactory.class);

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
    @SuppressWarnings("unused")
    public static void ignoreAttachOwnerFailure(Exception failure, ModelObject owner, String propertyName) {
        // Name the property the same way a successful attach would have, since this message exists to diagnose
        // exactly the properties that are missing that name.
        LOGGER.debug("Could not attach owner to {} because its getter failed, leaving the property without an owner.",
            displayNameFor(owner, propertyName), failure);
    }

    // Called from generated code
    @SuppressWarnings("unused")
    public void applyRole(Object value, ModelObject owner) {
        roleHandler.applyRoleTo(owner, value);
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type) {
        Object providedType = getManagedObjectRegistry().newInstance(type);
        if (providedType != null) {
            return attachOwner(providedType, owner, propertyName);
        }
        return attachOwner(instantiator.newInstanceWithDisplayName(type, displayNameFor(owner, propertyName)), owner, propertyName);
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type, Class<?> paramType) {
        Object providedType = getManagedObjectRegistry().newInstance(type, paramType);
        if (providedType != null) {
            return attachOwner(providedType, owner, propertyName);
        }
        throw new IllegalArgumentException("Unable to create an instance of type " + type.getName());
    }

    // Called from generated code
    public Object newInstance(ModelObject owner, String propertyName, Class<?> type, Class<?> keyType, Class<?> valueType) {
        Object providedType = getManagedObjectRegistry().newInstance(type, keyType, valueType);
        if (providedType != null) {
            return attachOwner(providedType, owner, propertyName);
        }
        throw new IllegalArgumentException("Unable to create an instance of type " + type.getName());
    }

    private static ManagedPropertyName displayNameFor(ModelObject owner, String propertyName) {
        if (owner.getModelIdentityDisplayName() instanceof ManagedPropertyName) {
            ManagedPropertyName root = (ManagedPropertyName) owner.getModelIdentityDisplayName();
            return new NestedManagedPropertyName(root, propertyName);
        } else {
            return new RootManagedPropertyName(cachedOwnerDisplayNameOf(owner), propertyName);
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

    private ManagedObjectRegistry getManagedObjectRegistry() {
        ManagedObjectRegistry managedObjectRegistry = (ManagedObjectRegistry) serviceLookup.find(ManagedObjectRegistry.class);
        if (managedObjectRegistry == null) {
            throw new IllegalStateException("No managed object registry found. ServiceLookup: " + serviceLookup);
        }
        return managedObjectRegistry;
    }

    @NullMarked
    private static abstract class ManagedPropertyName implements DisplayName {
        abstract String getPath();
        abstract @Nullable String getOwnerDisplayName();

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
            String ownerDisplayName = getOwnerDisplayName();
            if (ownerDisplayName != null) {
                return ownerDisplayName + " property '" + getPath() + "'";
            } else {
                return "property '" + getPath() + "'";
            }
        }
    }

    @NullMarked
    private static class RootManagedPropertyName extends ManagedPropertyName {
        private final Cached<String> ownerDisplayName;
        private final String propertyName;

        public RootManagedPropertyName(Cached<String> ownerDisplayName, String propertyName) {
            this.ownerDisplayName = ownerDisplayName;
            this.propertyName = propertyName;
        }

        @Override
        String getPath() {
            return propertyName;
        }

        @Override
        public @Nullable String getOwnerDisplayName() {
            return ownerDisplayName.get();
        }
    }

    @NullMarked
    private static class NestedManagedPropertyName extends ManagedPropertyName {
        private final ManagedPropertyName parent;
        private final String propertyName;

        public NestedManagedPropertyName(ManagedPropertyName parent, String propertyName) {
            this.parent = parent;
            this.propertyName = propertyName;
        }

        @Override
        public String getPath() {
            return parent.getPath() + "." + propertyName;
        }

        @Override
        @Nullable String getOwnerDisplayName() {
            return parent.getOwnerDisplayName();
        }
    }
}
