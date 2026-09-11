/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.LeafProblemGroup;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.SubProblemGroup;
import org.gradle.api.problems.TransformationProblemGroup;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * The predefined {@code Transformation} root group. Singleton, reachable through {@link DefaultProblemGroups}.
 */
final class PredefinedTransformationProblemGroup extends TransformationProblemGroup implements ResolvableProblemGroup, Serializable {

    static final String NAME = "Transformation";

    private final DefaultSubProblemGroup binaryTransformation = new DefaultSubProblemGroup("Binary Transformation", PredefinedProblemGroupDescriptions.TRANSFORMATION_BINARY, this);
    private final DefaultSubProblemGroup codeTransformation = new DefaultSubProblemGroup("Code Transformation", PredefinedProblemGroupDescriptions.TRANSFORMATION_CODE, this);
    private final DefaultSubProblemGroup resourceTransformation = new DefaultSubProblemGroup("Resource Transformation", PredefinedProblemGroupDescriptions.TRANSFORMATION_RESOURCE, this);
    private final DefaultLeafProblemGroup undefined = DefaultLeafProblemGroup.undefined(this, NAME);
    private final PredefinedChildren children = new PredefinedChildren(binaryTransformation, codeTransformation, resourceTransformation, undefined);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return PredefinedProblemGroupDescriptions.TRANSFORMATION;
    }

    @Override
    @Nullable
    public ProblemGroup getParent() {
        return null;
    }

    @Override
    public SubProblemGroup getBinaryTransformation() {
        return binaryTransformation;
    }

    @Override
    public SubProblemGroup getCodeTransformation() {
        return codeTransformation;
    }

    @Override
    public SubProblemGroup getResourceTransformation() {
        return resourceTransformation;
    }

    @Override
    public LeafProblemGroup getUndefined() {
        return undefined;
    }

    @Override
    public SubProblemGroup group(String name) {
        return children.group(this, name);
    }

    @Override
    public ResolvableProblemGroup resolveChild(String name) {
        return children.resolve(this, name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return ProblemGroupSupport.equals(this, o);
    }

    @Override
    public int hashCode() {
        return ProblemGroupSupport.hashCode(this);
    }

    @Override
    public String toString() {
        return ProblemGroupSupport.render(this);
    }

    private Object writeReplace() {
        return SerializedProblemGroup.of(this);
    }
}
