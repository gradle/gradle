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

import java.util.List;

public class UpgradedProperty {
    private final String propertyName;
    private final String methodName;
    private final String containingType;
    private final List<UpgradedMethod> upgradedMethods;

    public UpgradedProperty(String containingType, String propertyName, String methodName, List<UpgradedMethod> upgradedMethods) {
        this.containingType = containingType;
        this.propertyName = propertyName;
        this.methodName = methodName;
        this.upgradedMethods = upgradedMethods;
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

    public List<UpgradedMethod> getUpgradedMethods() {
        return upgradedMethods;
    }

    @Override
    public String toString() {
        return "UpgradedProperty{" +
            "propertyName='" + propertyName + '\'' +
            ", methodName='" + methodName + '\'' +
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
}
