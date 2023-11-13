/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Default implementation of {@link ResolvedArtifact}, the artifact type used by the legacy
 * {@link org.gradle.api.artifacts.ResolvedConfiguration} API. This class presents a file extension, type,
 * classifier via its {@link IvyArtifactName}. This name is tracked on a best-effort basis, and may not
 * always represent the actual file name.
 */
public class DefaultResolvedArtifact implements ResolvedArtifact {

    private final ComponentArtifactIdentifier id;
    private final CalculatedValue<File> fileSource;
    private final ModuleVersionIdentifier owner;
    private final IvyArtifactName artifactName;

    public DefaultResolvedArtifact(ComponentArtifactIdentifier id, CalculatedValue<File> fileSource, @Nullable ModuleVersionIdentifier owner, IvyArtifactName artifactName) {
        this.id = id;
        this.fileSource = fileSource;
        this.owner = owner;
        this.artifactName = artifactName;
    }

    @Override
    public File getFile() {
        fileSource.finalizeIfNotAlready();
        return fileSource.get();
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return id;
    }

    @Override
    public String toString() {
        return id.getDisplayName();
    }

    @Override
    public ResolvedModuleVersion getModuleVersion() {
        if (owner == null) {
            // Local file dependencies do not have an owner
            throw new UnsupportedOperationException();
        }
        return new DefaultResolvedModuleVersion(owner);
    }

    @Override
    public String getName() {
        return artifactName.getName();
    }

    @Override
    public String getType() {
        return artifactName.getType();
    }

    @Nullable
    @Override
    public String getExtension() {
        return artifactName.getExtension();
    }

    @Nullable
    @Override
    public String getClassifier() {
        return artifactName.getClassifier();
    }
}
