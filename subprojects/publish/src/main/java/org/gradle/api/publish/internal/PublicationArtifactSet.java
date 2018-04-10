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

package org.gradle.api.publish.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.publish.PublicationArtifact;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class PublicationArtifactSet extends DefaultDomainObjectSet<PublicationArtifact> {

    private final String name;
    private final FileCollection files;

    public PublicationArtifactSet(String name, FileCollectionFactory fileCollectionFactory) {
        super(PublicationArtifact.class);
        this.name = name;
        files = fileCollectionFactory.create(new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                for (PublicationArtifact artifact : PublicationArtifactSet.this) {
                    context.add(artifact.getBuildDependencies());
                }
            }
        }, new MinimalFileSet() {
            @Override
            public String getDisplayName() {
                return PublicationArtifactSet.this.name;
            }

            @Override
            public Set<File> getFiles() {
                Set<File> result = new LinkedHashSet<File>();
                for (PublicationArtifact artifact : PublicationArtifactSet.this) {
                    result.add(artifact.getFile());
                }
                return result;
            }
        });
    }

    public FileCollection getFiles() {
        return files;
    }
}
