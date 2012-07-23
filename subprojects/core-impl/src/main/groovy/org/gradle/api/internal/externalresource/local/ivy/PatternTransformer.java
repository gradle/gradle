/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.externalresource.local.ivy;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.Transformer;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class PatternTransformer implements Transformer<Factory<List<File>>, ArtifactRevisionId> {

    private final String pattern;
    private DirectoryFileTree fileTree;

    public PatternTransformer(File baseDir, String pattern) {
        this.pattern = pattern;
        fileTree = new DirectoryFileTree(baseDir);
    }

    private String getArtifactPattern(ArtifactRevisionId artifactId) {
        String substitute = pattern;
        // Need to handle organisation values that have been munged for m2compatible
        substitute = IvyPatternHelper.substituteToken(substitute, "organisation", artifactId.getModuleRevisionId().getOrganisation().replace('/', '.'));
        substitute = IvyPatternHelper.substituteToken(substitute, "organisation-path", artifactId.getModuleRevisionId().getOrganisation().replace('.', '/'));

        Artifact dummyArtifact = new DefaultArtifact(artifactId, null, null, false);
        substitute = IvyPatternHelper.substitute(substitute, dummyArtifact);
        return substitute;
    }

    private DirectoryFileTree getMatchingFiles(ArtifactRevisionId artifact) {
        String patternString = getArtifactPattern(artifact);
        PatternFilterable pattern = new PatternSet();
        pattern.include(patternString);
        return fileTree.filter(pattern);
    }

    public Factory<List<File>> transform(final ArtifactRevisionId artifactId) {
        return new Factory<List<File>>() {
            public List<File> create() {
                final List<File> files = new LinkedList<File>();
                if (artifactId != null) {
                    getMatchingFiles(artifactId).visit(new EmptyFileVisitor() {
                        public void visitFile(FileVisitDetails fileDetails) {
                            files.add(fileDetails.getFile());
                        }
                    });
                }
                return files;
            }
        };
    }
}
