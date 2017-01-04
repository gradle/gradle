/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;

class ArtifactAttributeMatcher {

    private final AttributesSchema attributesSchema;

    public ArtifactAttributeMatcher(AttributesSchema attributesSchema) {
        this.attributesSchema = attributesSchema;
    }

    boolean attributesMatch(AttributeContainer artifact, AttributeContainer target, boolean incompleteCandidate) {
        if (artifact.getAttributes().isEmpty() && target.getAttributes().isEmpty()) {
            return true;
        }
        return ((AttributesSchemaInternal) attributesSchema).isMatching(artifact, target, incompleteCandidate);
    }

}
