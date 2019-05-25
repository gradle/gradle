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
package org.gradle.api.publish.maven.internal.artifact;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.internal.typeconversion.NotationParser;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultMavenArtifactSet extends DefaultDomainObjectSet<MavenArtifact> implements MavenArtifactSet, PublicationArtifactSet<MavenArtifact> {
    private final String publicationName;
    private final FileCollection files;
    private final NotationParser<Object, MavenArtifact> mavenArtifactParser;

    public DefaultMavenArtifactSet(String publicationName, NotationParser<Object, MavenArtifact> mavenArtifactParser, FileCollectionFactory fileCollectionFactory, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        super(MavenArtifact.class, collectionCallbackActionDecorator);
        this.publicationName = publicationName;
        this.mavenArtifactParser = mavenArtifactParser;
        this.files = fileCollectionFactory.create(new ArtifactsTaskDependency(), new ArtifactsFileCollection());
    }

    @Override
    public MavenArtifact artifact(Object source) {
        MavenArtifact artifact = mavenArtifactParser.parseNotation(source);
        add(artifact);
        return artifact;
    }

    @Override
    public MavenArtifact artifact(Object source, Action<? super MavenArtifact> config) {
        MavenArtifact artifact = artifact(source);
        config.execute(artifact);
        return artifact;
    }

    @Override
    public FileCollection getFiles() {
        return files;
    }

    private class ArtifactsFileCollection implements MinimalFileSet {
        @Override
        public String getDisplayName() {
            return "artifacts for Maven publication '" + publicationName + "'";
        }

        @Override
        public Set<File> getFiles() {
            Set<File> files = new LinkedHashSet<File>();
            for (MavenArtifact artifact : DefaultMavenArtifactSet.this) {
                files.add(artifact.getFile());
            }
            return files;
        }
    }

    private class ArtifactsTaskDependency extends AbstractTaskDependency {
        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (MavenArtifact mavenArtifact : DefaultMavenArtifactSet.this) {
                context.add(mavenArtifact);
            }
        }
    }
}
