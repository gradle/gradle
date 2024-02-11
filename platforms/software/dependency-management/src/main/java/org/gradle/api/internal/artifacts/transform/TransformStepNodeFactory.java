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

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.id.ConfigurationCacheableIdFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Transform step node factory that ensures that unique ids are used correctly
 * when creating new instances and/or loading from the configuration cache.
 */
@ThreadSafe
@ServiceScope(Scopes.BuildTree.class)
public class TransformStepNodeFactory {

    private final ConfigurationCacheableIdFactory idFactory;

    public TransformStepNodeFactory(ConfigurationCacheableIdFactory idFactory) {
        this.idFactory = idFactory;
    }

    /**
     * Create an initial transform step node.
     */
    public TransformStepNode.InitialTransformStepNode createInitial(
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformStep initial,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        long transformStepNodeId = idFactory.createId();
        return new TransformStepNode.InitialTransformStepNode(transformStepNodeId, targetComponentVariant, sourceAttributes, initial, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    /**
     * Create an initial transform step node.
     * <p>
     * Should only be used when loading from the configuration cache to set the node id.
     */
    public TransformStepNode.InitialTransformStepNode recreateInitial(
        long transformStepNodeId,
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformStep initial,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        idFactory.idRecreated();
        return new TransformStepNode.InitialTransformStepNode(transformStepNodeId, targetComponentVariant, sourceAttributes, initial, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    /**
     * Create a chained transform step node.
     */
    public TransformStepNode.ChainedTransformStepNode createChained(
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformStep current,
        TransformStepNode previous,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        long transformStepNodeId = idFactory.createId();
        return new TransformStepNode.ChainedTransformStepNode(transformStepNodeId, targetComponentVariant, sourceAttributes, current, previous, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    /**
     * Create a chained transform step node.
     * <p>
     * Should only be used when loading from the configuration cache to set the node id.
     */
    public TransformStepNode.ChainedTransformStepNode recreateChained(
        long transformStepNodeId,
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformStep current,
        TransformStepNode previous,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        idFactory.idRecreated();
        return new TransformStepNode.ChainedTransformStepNode(transformStepNodeId, targetComponentVariant, sourceAttributes, current, previous, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

}
