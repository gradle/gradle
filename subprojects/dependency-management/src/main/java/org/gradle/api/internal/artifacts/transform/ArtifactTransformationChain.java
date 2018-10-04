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

import org.gradle.api.Action;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class ArtifactTransformationChain implements ArtifactTransformation {

    private final ArtifactTransformation first;
    private final ArtifactTransformation second;

    public ArtifactTransformationChain(ArtifactTransformation first, ArtifactTransformation second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public List<File> transform(File file) {
        List<File> result = new ArrayList<File>();
        for (File intermediate : first.transform(file)) {
            result.addAll(second.transform(intermediate));
        }
        return result;
    }

    @Override
    public TransformationSubject transform(TransformationSubject subject) {
        TransformationSubject intermediateSubject = first.transform(subject);
        return second.transform(intermediateSubject);
    }

    @Override
    public boolean hasCachedResult(File input) {
        if (first.hasCachedResult(input)) {
            for (File intermediate : first.transform(input)) {
                if (!second.hasCachedResult(intermediate)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public String getDisplayName() {
        return first.getDisplayName() + " -> " + second.getDisplayName();
    }

    @Override
    public void visitTransformationSteps(Action<? super ArtifactTransformation> action) {
        first.visitTransformationSteps(action);
        second.visitTransformationSteps(action);
    }
}
