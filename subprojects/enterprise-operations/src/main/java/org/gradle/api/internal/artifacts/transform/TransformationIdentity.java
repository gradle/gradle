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

import org.gradle.api.internal.artifacts.ComponentVariantIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationIdentity;
import org.gradle.internal.taskgraph.NodeIdentity;

import javax.annotation.Nullable;

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
     * Component variant produced by this transformation.
     */
    ComponentVariantIdentifier getTargetVariant();

    String getArtifactName();

    /**
     * Configuration that contains transitive dependencies of the input artifact.
     */
    @Nullable
    ConfigurationIdentity getDependenciesConfigurationIdentity();

    long getTransformationNodeId();

}
