/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;

import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

public class ResolveFailedTransformationSubject implements TransformationSubject{
    private final ComponentArtifactIdentifier artifactId;
    private final Throwable failure;

    public ResolveFailedTransformationSubject(ComponentArtifactIdentifier artifactId, Throwable failure) {
        this.artifactId = artifactId;
        this.failure = failure;
    }


    @Override
    public List<File> getFiles() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public String getDisplayName() {
        return "artifact " + artifactId.getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
