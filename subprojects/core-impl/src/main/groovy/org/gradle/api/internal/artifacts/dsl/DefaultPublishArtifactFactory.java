/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.apache.tools.ant.Task;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.TopLevelNotationParser;
import org.gradle.api.internal.notations.parsers.MapKey;
import org.gradle.api.internal.notations.parsers.MapNotationParser;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collection;

/**
 * @author Hans Dockter
 */
public class DefaultPublishArtifactFactory implements NotationParser<PublishArtifact>, TopLevelNotationParser {
    private final Instantiator instantiator;
    private final DependencyMetaDataProvider metaDataProvider;
    private final NotationParser<PublishArtifact> delegate;

    public DefaultPublishArtifactFactory(Instantiator instantiator, DependencyMetaDataProvider metaDataProvider) {
        this.instantiator = instantiator;
        this.metaDataProvider = metaDataProvider;
        FileNotationParser fileParser = new FileNotationParser();
        delegate = new NotationParserBuilder<PublishArtifact>()
                .resultingType(PublishArtifact.class)
                .parser(new ArchiveTaskNotationParser())
                .parser(new FileMapNotationParser(fileParser))
                .parser(fileParser)
                .toComposite();
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }

    public PublishArtifact parseNotation(Object notation) {
        return delegate.parseNotation(notation);
    }

    private class ArchiveTaskNotationParser extends TypedNotationParser<AbstractArchiveTask, PublishArtifact> {
        private ArchiveTaskNotationParser() {
            super(AbstractArchiveTask.class);
        }

        @Override
        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Instances of AbstractArchiveTask, e.g. jar.");
        }

        @Override
        protected PublishArtifact parseType(AbstractArchiveTask notation) {
            return instantiator.newInstance(ArchivePublishArtifact.class, notation);
        }
    }

    private class FileMapNotationParser extends MapNotationParser<PublishArtifact> {
        private final FileNotationParser fileParser;

        private FileMapNotationParser(FileNotationParser fileParser) {
            this.fileParser = fileParser;
        }

        protected PublishArtifact parseMap(@MapKey("file") File file) {
            return fileParser.parseType(file);
        }
    }

    private class FileNotationParser extends TypedNotationParser<File, PublishArtifact> {
        private FileNotationParser() {
            super(File.class);
        }

        @Override
        protected PublishArtifact parseType(File file) {
            Module module = metaDataProvider.getModule();
            ArtifactFile artifactFile = new ArtifactFile(file, module.getVersion());
            return instantiator.newInstance(DefaultPublishArtifact.class, artifactFile.getName(), artifactFile.getExtension(), artifactFile.getExtension(),
                                            artifactFile.getClassifier(), null, file, new Task[0]);
        }
    }
}
