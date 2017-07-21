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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.failures.ResolutionFailure;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class ResolvedFilesCollectingVisitor implements ArtifactVisitor {
    private final Set<File> files = Sets.newLinkedHashSet();
    private final Set<Throwable> failures = Sets.newLinkedHashSet();
    private final Set<ResolutionFailure<?>> resolutionFailures = Sets.newLinkedHashSet();

    @Override
    public void visitArtifact(AttributeContainer variant, ResolvableArtifact artifact) {
        try {
            File file = artifact.getFile(); // triggering file resolve
            this.files.add(file);
        } catch (Throwable t) {
            failures.add(t);
        }
    }

    @Override
    public boolean requireArtifactFiles() {
        return true;
    }

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public void visitResolutionFailure(ResolutionFailure<?> resolutionFailure) {
        resolutionFailures.add(resolutionFailure);
    }

    @Override
    public boolean includeFiles() {
        return true;
    }

    @Override
    public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
        this.files.add(file);
    }

    public Set<File> getFiles() {
        return files;
    }

    public Collection<Throwable> getFailures() {
        return failures;
    }

    public Collection<ResolutionFailure<?>> getResolutionFailures() {
        return resolutionFailures;
    }
}
