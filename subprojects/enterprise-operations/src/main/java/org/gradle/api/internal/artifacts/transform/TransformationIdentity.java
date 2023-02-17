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

import org.gradle.api.internal.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationIdentity;
import org.gradle.api.internal.capabilities.Capability;
import org.gradle.internal.taskgraph.NodeIdentity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Identity of a transformation node in an execution plan.
 *
 * @since 8.1
 */
public interface TransformationIdentity extends NodeIdentity {

    @Override
    default NodeType getNodeType() {
        return NodeType.ARTIFACT_TRANSFORM;
    }

    /**
     * Path of an included build of the consumer project.
     */
    String getBuildPath();

    /**
     * Consumer project path within the build.
     */
    String getProjectPath();

    /**
     * The component identifier of the transformed artifact.
     */
    ComponentIdentifier getComponentId();

    /**
     * Target attributes of the transformed artifact.
     * <p>
     * The attributes include the source attributes of the artifact before the transformation and
     */
    Map<String, String> getTargetAttributes();

    /**
     * Capabilities of the variant of the transformed artifact.
     * <p>
     * Artifact transforms can only change attributes, so the capabilities remain unchanged throughout the transformation chain.
     */
    List<? extends Capability> getCapabilities();

    /**
     * Name of the source artifact being transformed.
     * <p>
     * This name remains the same throughout the transformation chain.
     */
    String getArtifactName();

    /**
     * Configuration that contains transitive dependencies of the input artifact.
     * <p>
     * Present only if the artifact transform implementation declares a dependency on the input artifact dependencies.
     */
    @Nullable
    ConfigurationIdentity getDependenciesConfigurationIdentity();

    /**
     * An opaque identifier distinguishes between different transformation nodes in case other identity properties are the same.
     */
    long getTransformationNodeId();

}
