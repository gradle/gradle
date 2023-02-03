/*
 * Copyright 2020 the original author or authors.
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

/**
 * A transformation with all of its parameters bound to their providers. A transformer may take parameters from a parameters
 * object specified when the transformation is registered and may also take the upstream dependencies of the source artifact
 * as a parameter.
 */
public class BoundTransformationStep {
    private final TransformationStep transformation;
    private final TransformUpstreamDependencies upstreamDependencies;

    public BoundTransformationStep(TransformationStep transformation, TransformUpstreamDependencies upstreamDependencies) {
        this.transformation = transformation;
        this.upstreamDependencies = upstreamDependencies;
    }

    public TransformationStep getTransformation() {
        return transformation;
    }

    public TransformUpstreamDependencies getUpstreamDependencies() {
        return upstreamDependencies;
    }
}
