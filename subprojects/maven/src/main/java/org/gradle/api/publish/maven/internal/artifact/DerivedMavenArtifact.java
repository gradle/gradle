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

package org.gradle.api.publish.maven.internal.artifact;

import org.gradle.api.internal.tasks.TaskDependencies;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.publish.maven.MavenArtifact;

import java.io.File;

import static com.google.common.io.Files.getFileExtension;

public class DerivedMavenArtifact extends AbstractMavenArtifact {
    private final MavenArtifact original;
    private final File derivedFile;
    private final String extension;

    public DerivedMavenArtifact(MavenArtifact original, File derivedFile) {
        this.original = original;
        this.derivedFile = derivedFile;
        this.extension = original.getExtension() + "." + getFileExtension(derivedFile.getName());
    }

    @Override
    public File getFile() {
        return derivedFile;
    }

    @Override
    protected String getDefaultExtension() {
        return extension;
    }

    @Override
    protected String getDefaultClassifier() {
        return original.getClassifier();
    }

    @Override
    protected TaskDependencyInternal getDefaultBuildDependencies() {
        return TaskDependencies.EMPTY;
    }
}
