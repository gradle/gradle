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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.operations.dependencies.variants.ComponentIdentifier;
import org.gradle.operations.dependencies.variants.OpaqueComponentIdentifier;

public class ComponentToOperationConverter {

    public static ComponentIdentifier convertComponentIdentifier(org.gradle.api.artifacts.component.ComponentIdentifier componentId) {
        if (componentId instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifier projectComponentIdentifier = (ProjectComponentIdentifier) componentId;
            return new org.gradle.operations.dependencies.variants.ProjectComponentIdentifier() {
                @Override
                public String getBuildPath() {
                    return projectComponentIdentifier.getBuild().getBuildPath();
                }

                @Override
                public String getProjectPath() {
                    return projectComponentIdentifier.getProjectPath();
                }

                @Override
                public String toString() {
                    return projectComponentIdentifier.getDisplayName();
                }
            };
        } else if (componentId instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) componentId;
            return new org.gradle.operations.dependencies.variants.ModuleComponentIdentifier() {
                @Override
                public String getGroup() {
                    return moduleComponentIdentifier.getGroup();
                }

                @Override
                public String getModule() {
                    return moduleComponentIdentifier.getModule();
                }

                @Override
                public String getVersion() {
                    return moduleComponentIdentifier.getVersion();
                }

                @Override
                public String toString() {
                    return moduleComponentIdentifier.getDisplayName();
                }
            };
        } else {
            return new OpaqueComponentIdentifier() {
                @Override
                public String getDisplayName() {
                    return componentId.getDisplayName();
                }

                @Override
                public String getClassName() {
                    return componentId.getClass().getName();
                }

                @Override
                public String toString() {
                    return componentId.getDisplayName();
                }
            };
        }
    }
}
