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

package org.gradle.internal.component.resolution.failure.type;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;

import java.util.List;

/**
 * A {@link ResolutionFailure} that represents the situation when multiple artifact transforms are
 * available that would satisfy a dependency selection request.
 */
public final class AmbiguousArtifactTransformFailure extends AbstractIncompatibleAttributesSelectionFailure {
    private final ImmutableList<TransformedVariant> transformedVariants;

    public AmbiguousArtifactTransformFailure(AttributesSchemaInternal schema, String requestedName, AttributeContainerInternal requestedAttributes, List<TransformedVariant> transformedVariants) {
        super(schema, requestedName, requestedAttributes);
        this.transformedVariants = ImmutableList.copyOf(transformedVariants);
    }

    public ImmutableList<TransformedVariant> getTransformedVariants() {
        return transformedVariants;
    }
}
