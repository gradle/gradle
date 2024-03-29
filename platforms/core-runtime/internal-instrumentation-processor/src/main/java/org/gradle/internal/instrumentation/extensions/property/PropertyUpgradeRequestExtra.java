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

import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty.BinaryCompatibility;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader.DeprecationSpec;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.GradleLazyType;

class PropertyUpgradeRequestExtra implements RequestExtra {

    private final String propertyName;
    private final boolean isFluentSetter;
    private final String implementationClassName;
    private final String interceptedPropertyAccessorName;
    private final String interceptedPropertyAccessorDescriptor;
    private final GradleLazyType upgradedPropertyType;
    private final DeprecationSpec deprecationSpec;
    private final BinaryCompatibility binaryCompatibility;

    public PropertyUpgradeRequestExtra(
        String propertyName,
        boolean isFluentSetter,
        String implementationClassName,
        String interceptedPropertyAccessorName,
        String interceptedPropertyAccessorDescriptor,
        GradleLazyType upgradedPropertyType,
        DeprecationSpec deprecationSpec,
        BinaryCompatibility binaryCompatibility
    ) {
        this.propertyName = propertyName;
        this.isFluentSetter = isFluentSetter;
        this.implementationClassName = implementationClassName;
        this.interceptedPropertyAccessorName = interceptedPropertyAccessorName;
        this.interceptedPropertyAccessorDescriptor = interceptedPropertyAccessorDescriptor;
        this.upgradedPropertyType = upgradedPropertyType;
        this.deprecationSpec = deprecationSpec;
        this.binaryCompatibility = binaryCompatibility;
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

    public String getInterceptedPropertyAccessorDescriptor() {
        return interceptedPropertyAccessorDescriptor;
    }

    public GradleLazyType getUpgradedPropertyType() {
        return upgradedPropertyType;
    }

    public boolean isFluentSetter() {
        return isFluentSetter;
    }

    public DeprecationSpec getDeprecationSpec() {
        return deprecationSpec;
    }

    public BinaryCompatibility getBinaryCompatibility() {
        return binaryCompatibility;
    }
}
