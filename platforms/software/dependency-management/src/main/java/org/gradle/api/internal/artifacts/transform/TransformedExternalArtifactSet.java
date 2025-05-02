/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;

/**
 * An artifact set containing transformed external artifacts.
 */
public class TransformedExternalArtifactSet extends AbstractTransformedArtifactSet {
    public TransformedExternalArtifactSet(
        ComponentIdentifier componentIdentifier,
        ResolvedArtifactSet delegate,
        ImmutableAttributes target,
        ImmutableCapabilities capabilities,
        TransformChain transformChain,
        TransformUpstreamDependenciesResolver dependenciesResolver,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        super(componentIdentifier, delegate, target, capabilities, transformChain, dependenciesResolver, calculatedValueContainerFactory);
    }

    public TransformedExternalArtifactSet(CalculatedValueContainer<ImmutableList<Artifacts>, AbstractTransformedArtifactSet.CalculateArtifacts> result) {
        super(result);
    }
}
