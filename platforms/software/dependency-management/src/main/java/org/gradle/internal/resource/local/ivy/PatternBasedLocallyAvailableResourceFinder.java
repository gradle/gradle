/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.local.ivy;

import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree;
import org.gradle.internal.Factory;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resource.local.AbstractLocallyAvailableResourceFinder;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class PatternBasedLocallyAvailableResourceFinder extends AbstractLocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> {

    public PatternBasedLocallyAvailableResourceFinder(File baseDir, ResourcePattern pattern, ChecksumService checksumService) {
        super(createProducer(baseDir, pattern), checksumService);
    }

    private static InternalTransformer<Factory<List<File>>, ModuleComponentArtifactMetadata> createProducer(final File baseDir, final ResourcePattern pattern) {
        return new InternalTransformer<Factory<List<File>>, ModuleComponentArtifactMetadata>() {
            @Override
            public Factory<List<File>> transform(final ModuleComponentArtifactMetadata artifact) {
                return () -> {
                    final List<File> files = new LinkedList<>();
                    if (artifact != null) {
                        getMatchingFiles(artifact).visit(new EmptyFileVisitor() {
                            @Override
                            public void visitFile(FileVisitDetails fileDetails) {
                                files.add(fileDetails.getFile());
                            }
                        });
                    }
                    return files;
                };
            }

            private MinimalFileTree getMatchingFiles(ModuleComponentArtifactMetadata artifact) {
                String patternString = pattern.getLocation(artifact).getPath();
                return new SingleIncludePatternFileTree(baseDir, patternString);
            }

        };
    }
}
