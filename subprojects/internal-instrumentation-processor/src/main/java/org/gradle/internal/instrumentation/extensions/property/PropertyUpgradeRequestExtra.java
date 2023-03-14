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
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.objectweb.asm.Type;

class PropertyUpgradeRequestExtra implements RequestExtra {

    enum UpgradedPropertyType {
        PROPERTY,
        FILE_SYSTEM_LOCATION_PROPERTY,
        CONFIGURABLE_FILE_COLLECTION
        ;

        public static UpgradedPropertyType from(Type type) {
            if (type.getClassName().equals(DirectoryProperty.class.getName()) || type.getClassName().equals(RegularFileProperty.class.getName())) {
                return FILE_SYSTEM_LOCATION_PROPERTY;
            } else if (type.getClassName().equals(ConfigurableFileCollection.class.getName())) {
                return CONFIGURABLE_FILE_COLLECTION;
            } else {
                return PROPERTY;
            }
        }
    }

    private final String implementationClassName;
    private final String interceptedPropertyGetterName;
    private final UpgradedPropertyType upgradedPropertyType;

    public PropertyUpgradeRequestExtra(String implementationClassName, String interceptedPropertyGetterName, UpgradedPropertyType upgradedPropertyType) {
        this.implementationClassName = implementationClassName;
        this.interceptedPropertyGetterName = interceptedPropertyGetterName;
        this.upgradedPropertyType = upgradedPropertyType;
    }

    public String getImplementationClassName() {
        return implementationClassName;
    }

    public String getInterceptedPropertyGetterName() {
        return interceptedPropertyGetterName;
    }

    public UpgradedPropertyType getUpgradedPropertyType() {
        return upgradedPropertyType;
    }
}
