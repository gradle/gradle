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

package gradlebuild.binarycompatibility.upgrades;

import com.google.common.collect.ImmutableList;
import japicmp.model.JApiMethod;

import java.util.List;
import java.util.Objects;

public class UpgradedProperty {
    private final String propertyName;
    private final String methodName;
    private final String methodDescriptor;
    private final String containingType;
    private final List<UpgradedMethod> upgradedMethods;

    public UpgradedProperty(String containingType, String propertyName, String methodName, String methodDescriptor, List<UpgradedMethod> upgradedMethods) {
        this.containingType = containingType;
        this.propertyName = propertyName;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.upgradedMethods = ImmutableList.copyOf(upgradedMethods);
    }

    public String getContainingType() {
        return containingType;
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

    public List<UpgradedMethod> getUpgradedMethods() {
        return upgradedMethods;
    }

    @Override
    public String toString() {
        return "UpgradedProperty{" +
            "propertyName='" + propertyName + '\'' +
            ", methodName='" + methodName + '\'' +
            ", methodDescriptor='" + methodDescriptor + '\'' +
            ", containingType='" + containingType + '\'' +
            ", upgradedMethods=" + upgradedMethods +
            '}';
    }

    public static class UpgradedMethod {
        private final String name;
        private final String descriptor;

        public UpgradedMethod(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public String toString() {
            return "UpgradedMethod{" +
                "name='" + name + '\'' +
                ", descriptor='" + descriptor + '\'' +
                '}';
        }
    }

    public static class MethodKey {
        private final String containingType;
        private final String methodName;
        private final String descriptor;

        public MethodKey(String containingType, String methodName, String descriptor) {
            this.containingType = containingType;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public String getContainingType() {
            return containingType;
        }

        public static MethodKey ofUpgradedProperty(UpgradedProperty upgradedProperty) {
            return new MethodKey(upgradedProperty.getContainingType(), upgradedProperty.getMethodName(), upgradedProperty.getMethodDescriptor());
        }

        public static MethodKey ofUpgradedMethod(String containingType, UpgradedMethod upgradedMethod) {
            return new MethodKey(containingType, upgradedMethod.getName(), upgradedMethod.getDescriptor());
        }

        public static MethodKey ofNewMethod(JApiMethod jApiMethod) {
            String name = jApiMethod.getName();
            String descriptor = jApiMethod.getNewMethod().get().getSignature();
            String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
            return new MethodKey(containingType, name, descriptor);
        }

        public static MethodKey ofOldMethod(JApiMethod jApiMethod) {
            String name = jApiMethod.getName();
            String descriptor = jApiMethod.getOldMethod().get().getSignature();
            String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
            return new MethodKey(containingType, name, descriptor);
        }

        @Override
        public String toString() {
            return containingType + "#" + methodName + descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodKey that = (MethodKey) o;
            return Objects.equals(containingType, that.containingType) && Objects.equals(methodName, that.methodName) && Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(containingType, methodName, descriptor);
        }
    }
}
