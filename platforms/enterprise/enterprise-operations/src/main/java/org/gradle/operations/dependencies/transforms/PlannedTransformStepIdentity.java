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

package org.gradle.operations.dependencies.transforms;

import org.gradle.internal.taskgraph.NodeIdentity;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;
import org.gradle.operations.dependencies.variants.Capability;
import org.gradle.operations.dependencies.variants.ComponentIdentifier;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Identity of a transform step node in an execution plan.
 *
 * @since 8.1
 */
public interface PlannedTransformStepIdentity extends NodeIdentity {

    /**
     * Path of an included build of the consumer project.
     */
    String getConsumerBuildPath();

    /**
     * Consumer project path within the build.
     */
    String getConsumerProjectPath();

    /**
     * The component identifier of the transformed artifact.
     */
    ComponentIdentifier getComponentId();

    /**
     * Full set of attributes of the artifact before the transform.
     */
    Map<String, String> getSourceAttributes();

    /**
     * Target attributes of the transformed artifact.
     * <p>
     * The attributes include all source attributes of the artifact before the transform,
     * values for some of which have been changed by the transform.
     */
    Map<String, String> getTargetAttributes();

    /**
     * Capabilities of the variant of the transformed artifact.
     * <p>
     * Artifact transforms can only change attributes, so the capabilities remain unchanged throughout the transform chain.
     */
    List<? extends Capability> getCapabilities();

    /**
     * Name of the source artifact being transformed.
     * <p>
     * This name remains the same throughout the transform chain.
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
     * An opaque identifier distinguishes between different transform step nodes in case other identity properties are the same.
     */
    long getTransformStepNodeId();

}
