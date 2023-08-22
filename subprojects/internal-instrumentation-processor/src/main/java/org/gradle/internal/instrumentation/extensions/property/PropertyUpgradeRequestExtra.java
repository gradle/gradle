/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.extensions.property;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.objectweb.asm.Type;

class PropertyUpgradeRequestExtra implements RequestExtra {

    enum UpgradedPropertyType {
        LIST_PROPERTY(true),
        SET_PROPERTY(true),
        MAP_PROPERTY(true),
        PROPERTY(false),
        FILE_SYSTEM_LOCATION_PROPERTY(false),
        CONFIGURABLE_FILE_COLLECTION(false);

        private final boolean isMultiValueProperty;

        private UpgradedPropertyType(boolean isMultiValueProperty) {
            this.isMultiValueProperty = isMultiValueProperty;
        }

        public boolean isMultiValueProperty() {
            return isMultiValueProperty;
        }

        public static UpgradedPropertyType from(Type type) {
            if (type.getClassName().equals(DirectoryProperty.class.getName()) || type.getClassName().equals(RegularFileProperty.class.getName())) {
                return FILE_SYSTEM_LOCATION_PROPERTY;
            } else if (type.getClassName().equals(ConfigurableFileCollection.class.getName())) {
                return CONFIGURABLE_FILE_COLLECTION;
            } else if (type.getClassName().equals(MapProperty.class.getName())) {
                return MAP_PROPERTY;
            } else if (type.getClassName().equals(SetProperty.class.getName())) {
                return SET_PROPERTY;
            } else if (type.getClassName().equals(ListProperty.class.getName())) {
                return LIST_PROPERTY;
            } else {
                return PROPERTY;
            }
        }
    }

    private final String propertyName;
    private final boolean isFluentSetter;
    private final String implementationClassName;
    private final String interceptedPropertyAccessorName;
    private final UpgradedPropertyType upgradedPropertyType;

    public PropertyUpgradeRequestExtra(String propertyName, boolean isFluentSetter, String implementationClassName, String interceptedPropertyAccessorName, UpgradedPropertyType upgradedPropertyType) {
        this.propertyName = propertyName;
        this.isFluentSetter = isFluentSetter;
        this.implementationClassName = implementationClassName;
        this.interceptedPropertyAccessorName = interceptedPropertyAccessorName;
        this.upgradedPropertyType = upgradedPropertyType;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getImplementationClassName() {
        return implementationClassName;
    }

    public String getInterceptedPropertyAccessorName() {
        return interceptedPropertyAccessorName;
    }

    public UpgradedPropertyType getUpgradedPropertyType() {
        return upgradedPropertyType;
    }

    public boolean isFluentSetter() {
        return isFluentSetter;
    }
}
