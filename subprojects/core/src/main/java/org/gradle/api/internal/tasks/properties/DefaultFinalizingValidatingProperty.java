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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.util.internal.DeferredUtil;
import org.jspecify.annotations.Nullable;

public class DefaultFinalizingValidatingProperty extends AbstractValidatingProperty {
    private final PropertyValue value;
    private final boolean validateAbsentProviderElements;
    private LifecycleAwareValue lifecycleAware;

    public DefaultFinalizingValidatingProperty(String propertyName, PropertyValue value, boolean optional, ValidationAction validationAction) {
        this(propertyName, value, optional, false, validationAction);
    }

    public DefaultFinalizingValidatingProperty(
        String propertyName,
        PropertyValue value,
        boolean optional,
        InputFilePropertyType filePropertyType,
        ValidationAction validationAction
    ) {
        this(propertyName, value, optional, filePropertyType == InputFilePropertyType.FILES, validationAction);
    }

    private DefaultFinalizingValidatingProperty(
        String propertyName,
        PropertyValue value,
        boolean optional,
        boolean validateAbsentProviderElements,
        ValidationAction validationAction
    ) {
        super(propertyName, value, optional, validationAction);
        this.value = value;
        this.validateAbsentProviderElements = validateAbsentProviderElements;
    }

    @Override
    protected boolean isPresent(@Nullable Object value) {
        if (validateAbsentProviderElements && hasAbsentProvider(value)) {
            return false;
        }
        return super.isPresent(value);
    }

    @Override
    protected boolean hasConfigurableValue(@Nullable Object value) {
        if (validateAbsentProviderElements && hasAbsentProvider(value)) {
            return true;
        }
        return super.hasConfigurableValue(value);
    }

    private static boolean hasAbsentProvider(@Nullable Object value) {
        Object unpacked = DeferredUtil.unpackNestableDeferred(value);
        if (unpacked instanceof Provider) {
            return !((Provider<?>) unpacked).isPresent();
        }
        // FileCollection and DomainObjectCollection are live Iterable types; iterating them here would resolve them during presence validation.
        if (unpacked instanceof FileCollection || unpacked instanceof DomainObjectCollection) {
            return false;
        }
        if (unpacked instanceof Iterable) {
            for (Object element : (Iterable<?>) unpacked) {
                if (hasAbsentProvider(element)) {
                    return true;
                }
            }
        } else if (unpacked instanceof Object[]) {
            for (Object element : (Object[]) unpacked) {
                if (hasAbsentProvider(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void prepareValue() {
        super.prepareValue();
        Object obj = value.call();
        // TODO - move this to PropertyValue instead
        if (obj instanceof LifecycleAwareValue) {
            lifecycleAware = (LifecycleAwareValue) obj;
            lifecycleAware.prepareValue();
        }
    }

    @Override
    public void cleanupValue() {
        if (lifecycleAware != null) {
            lifecycleAware.cleanupValue();
        }
    }
}
