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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.DisplayName;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ResolvedFilesCollectingVisitor implements ArtifactVisitor {
    private final Set<File> files = Sets.newLinkedHashSet();
    private final Set<Throwable> failures = Sets.newLinkedHashSet();

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, List<? extends Capability> capabilities, ResolvableArtifact artifact) {
        try {
            File file = artifact.getFile();
            this.files.add(file);
        } catch (Exception t) {
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

    public Set<File> getFiles() {
        return files;
    }

    public Collection<Throwable> getFailures() {
        return failures;
    }
}
