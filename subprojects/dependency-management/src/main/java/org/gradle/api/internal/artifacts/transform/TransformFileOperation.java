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

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.io.File;
import java.util.List;

class TransformFileOperation implements RunnableBuildOperation {
    private final File file;
    private final ArtifactTransformer transform;
    private Throwable failure;
    private List<File> result;

    TransformFileOperation(File file, ArtifactTransformer transform) {
        this.file = file;
        this.transform = transform;
    }

    @Override
    public void run(BuildOperationContext context) {
        try {
            result = transform.transform(file);
        } catch (Throwable t) {
            failure = t;
        }
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        return BuildOperationDescriptor.displayName("Apply " + transform.getDisplayName() + " to " + file);
    }

    public Throwable getFailure() {
        return failure;
    }

    public List<File> getResult() {
        return result;
    }
}
