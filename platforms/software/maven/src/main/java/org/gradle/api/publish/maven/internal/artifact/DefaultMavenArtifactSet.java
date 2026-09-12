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
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.publish.internal.PublicationArtifactSet;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;

@SuppressWarnings("this-escape")
public class DefaultMavenArtifactSet extends DefaultDomainObjectSet<MavenArtifact> implements MavenArtifactSet, PublicationArtifactSet<MavenArtifact> {
    private final FileCollection files;
    private final NotationParser<Object, MavenArtifact> mavenArtifactParser;

    @Inject
    public DefaultMavenArtifactSet(
        String publicationName,
        NotationParser<Object, MavenArtifact> mavenArtifactParser,
        TaskDependencyFactory taskDependencyFactory,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator
    ) {
        super(MavenArtifact.class, collectionCallbackActionDecorator);
        this.mavenArtifactParser = mavenArtifactParser;
        this.files = new MavenArtifactsFileCollection(publicationName, this, taskDependencyFactory);
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
}
