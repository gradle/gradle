/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.file.FileCollectionInternal.OTHER;

/**
 * An {@link ArtifactVisitor} which proxies a {@link FileCollectionStructureVisitor} by
 * collecting visited artifacts and forwarding their files to the proxied visitor. If any
 * collected file has the same name as another, this visitor will attempt to rename the
 * conflicting files to a more precise name.
 * <p>
 * After the artifacts are collected, users of this class are expected to manually call
 * {@link #visitCollectedFiles()} in order to propagate the collected files to the proxied visitor.
 */
public class NameConflictResolvingFileCollectingVisitor implements ArtifactVisitor {

    private final FileCollectionStructureVisitor visitor;
    private final FileCollectionFactory fileCollectionFactory;
    private final Factory<File> temporaryDirFactory;

    private final Map<String, List<ResolvableArtifact>> allArtifacts = new HashMap<>();
    private final Set<Throwable> failures = new LinkedHashSet<>();

    public NameConflictResolvingFileCollectingVisitor(FileCollectionStructureVisitor visitor, FileCollectionFactory fileCollectionFactory, Factory<File> temporaryDirFactory) {
        this.visitor = visitor;
        this.fileCollectionFactory =  fileCollectionFactory;
        this.temporaryDirFactory = temporaryDirFactory;
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return visitor.prepareForVisit(source);
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, List<? extends Capability> capabilities, ResolvableArtifact artifact) {
        try {
            File file = artifact.getFile();
            allArtifacts.computeIfAbsent(file.getName(), k -> new ArrayList<>()).add(artifact);
        } catch (Exception e) {
            failures.add(e);
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
    public void visitSpec(FileCollectionInternal spec) {
        spec.visitStructure(visitor);
    }

    public Collection<Throwable> getFailures() {
        return failures;
    }

    /**
     * Must be called after all artifacts have been visited. Performs conflict resolution
     * and propagates the collected artifact files to the proxied visitor.
     */
    public void visitCollectedFiles() {
        for (List<ResolvableArtifact> artifacts : allArtifacts.values()) {
            if (artifacts.size() == 1) {
                visitor.visitCollection(OTHER, ImmutableList.of(artifacts.get(0).getFile()));
            } else {
                // These file names conflict with each other. Generate a new file with a more precise name.
                for (ResolvableArtifact toRename : artifacts) {
                    File sourceFile = toRename.getFile();
                    String renamedName = sourceFile.getName();

                    ComponentIdentifier id = toRename.getId().getComponentIdentifier();
                    if (id instanceof ModuleComponentIdentifier) {
                        ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) id;
                        renamedName = moduleId.getGroup().replace(".", "-") + "-" + renamedName;
                    }

                    fileCollectionFactory.renamed(sourceFile, renamedName, temporaryDirFactory).visitStructure(visitor);
                }
            }
        }
    }
}
