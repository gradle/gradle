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
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationParser;
import org.gradle.internal.typeconversion.TypedNotationParser;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collection;

public class PublishArtifactNotationParserFactory implements Factory<NotationParser<Object, PublishArtifact>> {
    private final Instantiator instantiator;
    private final DependencyMetaDataProvider metaDataProvider;

    public PublishArtifactNotationParserFactory(Instantiator instantiator, DependencyMetaDataProvider metaDataProvider) {
        this.instantiator = instantiator;
        this.metaDataProvider = metaDataProvider;
    }

    public NotationParser<Object, PublishArtifact> create() {
        FileNotationParser fileParser = new FileNotationParser();
        return new NotationParserBuilder<PublishArtifact>()
                .resultingType(PublishArtifact.class)
                .parser(new ArchiveTaskNotationParser())
                .parser(new FileMapNotationParser(fileParser))
                .parser(fileParser)
                .toComposite();
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
            return instantiator.newInstance(DefaultPublishArtifact.class, artifactFile.getName(), artifactFile.getExtension(),
                                            artifactFile.getExtension() == null? "":artifactFile.getExtension(),
                                            artifactFile.getClassifier(), null, file, new Task[0]);
        }
    }
}
