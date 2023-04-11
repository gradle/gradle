/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedFileVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;

import java.io.File;
import java.util.Set;

public class ResolvedFileCollectionVisitor implements ResolvedFileVisitor {
    private final FileCollectionStructureVisitor visitor;
    private final Set<File> files = Sets.newLinkedHashSet();
    private final Set<Throwable> failures = Sets.newLinkedHashSet();

    public ResolvedFileCollectionVisitor(FileCollectionStructureVisitor visitor) {
        this.visitor = visitor;
    }

    public Set<Throwable> getFailures() {
        return failures;
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return visitor.prepareForVisit(source);
    }

    @Override
    public void visitFile(File file) {
        files.add(file);
    }

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public void endVisitCollection(FileCollectionInternal.Source source) {
        visitor.visitCollection(source, files);
        files.clear();
    }
}
