/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.describer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.IncompatibleArtifactVariantsException;
import org.gradle.internal.component.resolution.failure.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.ResolutionFailure.ResolutionFailureType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class IncompatibleArtifactVariantsFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleArtifactVariantsException> {
    @Inject
    public IncompatibleArtifactVariantsFailureDescriber(DocumentationRegistry documentationRegistry) {
        super(documentationRegistry);
    }

    @Override
    public boolean canDescribeFailure(ResolutionFailure failure) {
        return failure.getType() == ResolutionFailureType.INCOMPATIBLE_ARTIFACT_VARIANTS;
    }

    @Override
    public IncompatibleArtifactVariantsException describeFailure(ResolutionFailure failure) {
        return new IncompatibleArtifactVariantsException("BLAH BLAH BLAH I NEED TO BE WRITTEN");
        turn incompatible nodes into candidates?
    }

    private String buildIncompatibleArtifactVariantsFailureMsg(ComponentState selected, Set<NodeState> incompatibleNodes) {
        StringBuilder sb = new StringBuilder("Multiple incompatible variants of ")
            .append(selected.getId())
            .append(" were selected:\n");
        ArrayList<NodeState> sorted = Lists.newArrayList(incompatibleNodes);
        sorted.sort(Comparator.comparing(NodeState::getNameWithVariant));
        for (NodeState node : sorted) {
            sb.append("   - Variant ").append(node.getNameWithVariant()).append(" has attributes ");
            formatAttributes(sb, node.getMetadata().getAttributes());
            sb.append("\n");
        }
        return sb.toString();
    }

    private void formatAttributes(StringBuilder sb, ImmutableAttributes attributes) {
        ImmutableSet<Attribute<?>> keySet = attributes.keySet();
        List<Attribute<?>> sorted = Lists.newArrayList(keySet);
        sorted.sort(Comparator.comparing(Attribute::getName));
        boolean space = false;
        sb.append("{");
        for (Attribute<?> attribute : sorted) {
            if (space) {
                sb.append(", ");
            }
            sb.append(attribute.getName()).append("=").append(attributes.getAttribute(attribute));
            space = true;
        }
        sb.append("}");
    }
}
