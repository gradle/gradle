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

import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity;
import org.gradle.operations.dependencies.variants.Capability;
import org.gradle.operations.dependencies.variants.ComponentIdentifier;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultPlannedTransformStepIdentity implements PlannedTransformStepIdentity {

    private final String consumerBuildPath;
    private final String consumerProjectPath;
    private final ComponentIdentifier componentId;
    private final Map<String, String> sourceAttributes;
    private final Map<String, String> targetAttributes;
    private final List<Capability> capabilities;
    private final String artifactName;
    private final ConfigurationIdentity dependenciesConfigurationIdentity;
    private final long transformStepNodeId;

    public DefaultPlannedTransformStepIdentity(
        String consumerBuildPath,
        String consumerProjectPath,
        ComponentIdentifier componentId,
        Map<String, String> sourceAttributes,
        Map<String, String> targetAttributes,
        List<Capability> capabilities,
        String artifactName,
        @Nullable
        ConfigurationIdentity dependenciesConfigurationIdentity,
        long transformStepNodeId
    ) {
        this.consumerBuildPath = consumerBuildPath;
        this.consumerProjectPath = consumerProjectPath;
        this.componentId = componentId;
        this.sourceAttributes = sourceAttributes;
        this.targetAttributes = targetAttributes;
        this.capabilities = capabilities;
        this.artifactName = artifactName;
        this.dependenciesConfigurationIdentity = dependenciesConfigurationIdentity;
        this.transformStepNodeId = transformStepNodeId;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.TRANSFORM_STEP;
    }

    @Override
    public String getConsumerBuildPath() {
        return consumerBuildPath;
    }

    @Override
    public String getConsumerProjectPath() {
        return consumerProjectPath;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return componentId;
    }

    @Override
    public Map<String, String> getSourceAttributes() {
        return sourceAttributes;
    }

    @Override
    public Map<String, String> getTargetAttributes() {
        return targetAttributes;
    }

    @Override
    public List<? extends Capability> getCapabilities() {
        return capabilities;
    }

    @Override
    public String getArtifactName() {
        return artifactName;
    }

    @Override
    public ConfigurationIdentity getDependenciesConfigurationIdentity() {
        return dependenciesConfigurationIdentity;
    }

    @Override
    public long getTransformStepNodeId() {
        return transformStepNodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPlannedTransformStepIdentity)) {
            return false;
        }
        DefaultPlannedTransformStepIdentity that = (DefaultPlannedTransformStepIdentity) o;
        return transformStepNodeId == that.transformStepNodeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transformStepNodeId);
    }

    @Override
    public String toString() {
        return "Transform '" + componentId + "' to " + targetAttributes;
    }
}
