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
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.IncompatibleArtifactVariantsException;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionFailure;
import org.gradle.internal.component.resolution.failure.ResolutionFailure.ResolutionFailureType;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;

public class IncompatibleArtifactVariantsFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleArtifactVariantsException> {
    private static final String INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at ";
    private static final String INCOMPATIBLE_VARIANTS_SECTION = "sub:variant-incompatible";

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
        String msg = buildIncompatibleArtifactVariantsFailureMsg(failure);
        IncompatibleArtifactVariantsException result = new IncompatibleArtifactVariantsException(msg);
        suggestSpecificDocumentation(result, INCOMPATIBLE_VARIANTS_PREFIX, INCOMPATIBLE_VARIANTS_SECTION);
        suggestReviewAlgorithm(result);
        return result;
    }

    private String buildIncompatibleArtifactVariantsFailureMsg(ResolutionFailure failure) {
        StringBuilder sb = new StringBuilder("Multiple incompatible variants of ")
            .append(failure.getRequestedName())
            .append(" were selected:\n");
        for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
            sb.append("   - Variant ").append(candidate.getDisplayName()).append(" has attributes ");
            formatAttributes(sb, candidate.getAllCandidateAttributes());
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
