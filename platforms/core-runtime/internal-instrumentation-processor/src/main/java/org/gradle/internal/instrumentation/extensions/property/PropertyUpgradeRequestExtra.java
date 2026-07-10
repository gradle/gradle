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

import com.squareup.javapoet.TypeName;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader.DeprecationSpec;
import org.gradle.internal.instrumentation.model.RequestExtra;

import javax.lang.model.element.ExecutableElement;

class PropertyUpgradeRequestExtra implements RequestExtra {

    private final String propertyName;
    private final String methodName;
    private final TypeName returnType;
    private final String implementationClassName;
    private final String interceptedPropertyAccessorName;
    private final String methodDescriptor;
    private final TypeName newPropertyType;
    private final DeprecationSpec deprecationSpec;
    private final BinaryCompatibility binaryCompatibility;
    private final String interceptedPropertyName;
    private final BridgedMethodInfo bridgedMethodInfo;

    public PropertyUpgradeRequestExtra(
        String propertyName,
        String methodName,
        String methodDescriptor,
        TypeName returnType,
        String implementationClassName,
        String interceptedPropertyName,
        String interceptedPropertyAccessorName,
        TypeName newPropertyType,
        DeprecationSpec deprecationSpec,
        BinaryCompatibility binaryCompatibility,
        BridgedMethodInfo bridgedMethodInfo
    ) {
        this.propertyName = propertyName;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.returnType = returnType;
        this.newPropertyType = newPropertyType;
        this.implementationClassName = implementationClassName;
        this.interceptedPropertyName = interceptedPropertyName;
        this.interceptedPropertyAccessorName = interceptedPropertyAccessorName;
        this.deprecationSpec = deprecationSpec;
        this.binaryCompatibility = binaryCompatibility;
        this.bridgedMethodInfo = bridgedMethodInfo;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    public TypeName getNewPropertyType() {
        return newPropertyType;
    }

    public String getImplementationClassName() {
        return implementationClassName;
    }


    public String getInterceptedPropertyName() {
        return interceptedPropertyName;
    }

    public String getInterceptedPropertyAccessorName() {
        return interceptedPropertyAccessorName;
    }

    public TypeName getReturnType() {
        return returnType;
    }

    public DeprecationSpec getDeprecationSpec() {
        return deprecationSpec;
    }

    public BinaryCompatibility getBinaryCompatibility() {
        return binaryCompatibility;
    }

    public BridgedMethodInfo getBridgedMethodInfo() {
        return bridgedMethodInfo;
    }

    public static class BridgedMethodInfo {
        public enum BridgeType {
            ADAPTER_METHOD_BRIDGE,
            INSTANCE_METHOD_BRIDGE
        }

        private final ExecutableElement bridgedMethod;
        private final BridgeType bridgeType;

        public BridgedMethodInfo(ExecutableElement bridgedMethod, BridgeType bridgeType) {
            this.bridgedMethod = bridgedMethod;
            this.bridgeType = bridgeType;
        }

        public ExecutableElement getBridgedMethod() {
            return bridgedMethod;
        }

        public BridgeType getBridgeType() {
            return bridgeType;
        }
    }
}
