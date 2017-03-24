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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ResolvedFilesCollectingVisitor implements ArtifactVisitor {
    public final Collection<? super File> files;
    public final List<Throwable> failures = new ArrayList<Throwable>();
    public final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

    public ResolvedFilesCollectingVisitor(Collection<? super File> files) {
        this.files = files;
    }

    @Override
    public void visitArtifact(AttributeContainer variant, ResolvedArtifact artifact) {
        // Defer adding the artifacts until after all the file dependencies have been visited
        this.artifacts.add(artifact);
    }

    @Override
    public boolean canPerformPreemptiveDownload() {
        return true;
    }

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public boolean includeFiles() {
        return true;
    }

    @Override
    public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
        this.files.add(file);
    }

    public void addArtifacts() {
        for (ResolvedArtifact artifact : artifacts) {
            try {
                this.files.add(artifact.getFile());
            } catch (Throwable t) {
                failures.add(t);
            }
        }
    }
}
