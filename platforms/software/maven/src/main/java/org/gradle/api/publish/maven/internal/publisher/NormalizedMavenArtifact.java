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

package org.gradle.api.publish.maven.internal.publisher;

import org.gradle.api.NonNullApi;
import org.gradle.api.publish.internal.PublicationArtifactInternal;
import org.gradle.api.publish.maven.MavenArtifact;

import javax.annotation.Nullable;
import java.io.File;

@NonNullApi
public class NormalizedMavenArtifact {
    private final File file;
    private final String extension;
    private final String classifier;
    private final boolean shouldBePublished;

    public NormalizedMavenArtifact(MavenArtifact artifact) {
        PublicationArtifactInternal artifactInternal = (PublicationArtifactInternal) artifact;
        this.file = artifact.getFile().get().getAsFile();
        this.extension = artifact.getExtension().get();
        this.classifier = artifact.getClassifier().getOrNull();
        this.shouldBePublished = artifactInternal.shouldBePublished();
    }

    public String getExtension() {
        return extension;
    }

    @Nullable
    public String getClassifier() {
        return classifier;
    }

    public File getFile() {
        return file;
    }

    public boolean shouldBePublished() {
        return shouldBePublished;
    }
}
