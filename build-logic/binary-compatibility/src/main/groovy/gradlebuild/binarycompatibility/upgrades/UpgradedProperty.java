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
import com.google.gson.annotations.SerializedName;
import japicmp.model.JApiMethod;

import java.util.List;
import java.util.Objects;

public class UpgradedProperty {
    private final String containingType;
    private final String propertyName;
    private final String methodName;
    private final String methodDescriptor;

    /**
     * Was upgradedMethods originally, but got renamed to upgradedAccessors and then to replacedAccessors
     * can be removed once base version will be the one that also uses 'replacedAccessors'.
     */
    @SerializedName(value = "replacedAccessors", alternate = {"upgradedAccessors", "upgradedMethods"})
    private final List<ReplacedAccessor> replacedAccessors;

    public UpgradedProperty(String containingType, String propertyName, String methodName, String methodDescriptor, List<ReplacedAccessor> replacedAccessors) {
        this.containingType = containingType;
        this.propertyName = propertyName;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.replacedAccessors = ImmutableList.copyOf(replacedAccessors);
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

    public List<ReplacedAccessor> getReplacedAccessors() {
        return replacedAccessors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UpgradedProperty that = (UpgradedProperty) o;
        return containingType.equals(that.containingType) && propertyName.equals(that.propertyName) && methodName.equals(that.methodName) && methodDescriptor.equals(that.methodDescriptor) && replacedAccessors.equals(that.replacedAccessors);
    }

    @Override
    public int hashCode() {
        int result = containingType.hashCode();
        result = 31 * result + propertyName.hashCode();
        result = 31 * result + methodName.hashCode();
        result = 31 * result + methodDescriptor.hashCode();
        result = 31 * result + replacedAccessors.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "UpgradedProperty{" +
            "propertyName='" + propertyName + '\'' +
            ", methodName='" + methodName + '\'' +
            ", methodDescriptor='" + methodDescriptor + '\'' +
            ", containingType='" + containingType + '\'' +
            ", replacedAccessors=" + replacedAccessors +
            '}';
    }

    public static class ReplacedAccessor {
        private final String name;
        private final String descriptor;
        private final BinaryCompatibility binaryCompatibility;

        public ReplacedAccessor(String name, String descriptor, BinaryCompatibility binaryCompatibility) {
            this.name = name;
            this.descriptor = descriptor;
            this.binaryCompatibility = binaryCompatibility;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public BinaryCompatibility getBinaryCompatibility() {
            return binaryCompatibility;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ReplacedAccessor that = (ReplacedAccessor) o;
            return name.equals(that.name) && descriptor.equals(that.descriptor) && binaryCompatibility == that.binaryCompatibility;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + descriptor.hashCode();
            result = 31 * result + binaryCompatibility.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ReplacedAccessor{" +
                "name='" + name + '\'' +
                ", descriptor='" + descriptor + '\'' +
                ", binaryCompatibility='" + binaryCompatibility + '\'' +
                '}';
        }
    }

    public enum BinaryCompatibility {
        ACCESSORS_REMOVED,
        ACCESSORS_KEPT
    }

    public static class AccessorKey {
        private final String containingType;
        private final String methodName;
        private final String descriptor;

        private AccessorKey(String containingType, String methodName, String descriptor) {
            this.containingType = containingType;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        public static AccessorKey of(String containingType, String methodName, String descriptor) {
            return new AccessorKey(containingType, methodName, descriptor);
        }

        public static AccessorKey ofUpgradedProperty(UpgradedProperty upgradedProperty) {
            return new AccessorKey(upgradedProperty.getContainingType(), upgradedProperty.getMethodName(), upgradedProperty.getMethodDescriptor());
        }

        public static AccessorKey ofReplacedAccessor(String containingType, ReplacedAccessor replacedAccessor) {
            return new AccessorKey(containingType, replacedAccessor.getName(), replacedAccessor.getDescriptor());
        }

        public static AccessorKey ofMethodWithSameSignatureButNewName(String newName, JApiMethod jApiMethod) {
            String descriptor = jApiMethod.getNewMethod().get().getSignature();
            String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
            return new AccessorKey(containingType, newName, descriptor);
        }


        public static AccessorKey ofNewMethod(JApiMethod jApiMethod) {
            String name = jApiMethod.getName();
            String descriptor = jApiMethod.getNewMethod().get().getSignature();
            String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
            return new AccessorKey(containingType, name, descriptor);
        }

        public static AccessorKey ofOldMethod(JApiMethod jApiMethod) {
            String name = jApiMethod.getName();
            String descriptor = jApiMethod.getOldMethod().get().getSignature();
            String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
            return new AccessorKey(containingType, name, descriptor);
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
            AccessorKey that = (AccessorKey) o;
            return Objects.equals(containingType, that.containingType)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(containingType, methodName, descriptor);
        }
    }
}
