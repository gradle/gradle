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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.dsl.ArtifactFile;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.TopLevelNotationParser;
import org.gradle.api.internal.notations.api.UnsupportedNotationException;
import org.gradle.api.internal.notations.parsers.MapKey;
import org.gradle.api.internal.notations.parsers.MapNotationParser;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collection;

public class MavenArtifactNotationParser implements NotationParser<MavenArtifact>, TopLevelNotationParser {
    private final Instantiator instantiator;
    private final String version;
    private final NotationParser<MavenArtifact> delegate;

    public MavenArtifactNotationParser(Instantiator instantiator, String version, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.version = version;
        FileNotationParser fileNotationParser = new FileNotationParser(fileResolver);
        ArchiveTaskNotationParser archiveTaskNotationParser = new ArchiveTaskNotationParser();
        PublishArtifactNotationParser publishArtifactNotationParser = new PublishArtifactNotationParser();

        NotationParser<MavenArtifact> sourceNotationParser = new NotationParserBuilder<MavenArtifact>()
                .resultingType(MavenArtifact.class)
                .parser(archiveTaskNotationParser)
                .parser(publishArtifactNotationParser)
                .parser(fileNotationParser)
                .toComposite();

        MavenArtifactMapNotationParser mavenArtifactMapNotationParser = new MavenArtifactMapNotationParser(sourceNotationParser, fileNotationParser);

        NotationParserBuilder<MavenArtifact> parserBuilder = new NotationParserBuilder<MavenArtifact>()
                .resultingType(MavenArtifact.class)
                .parser(archiveTaskNotationParser)
                .parser(publishArtifactNotationParser)
                .parser(mavenArtifactMapNotationParser)
                .parser(fileNotationParser);

        delegate = parserBuilder.toComposite();
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }

    public MavenArtifact parseNotation(Object notation) {
        return delegate.parseNotation(notation);
    }

    private class ArchiveTaskNotationParser extends TypedNotationParser<AbstractArchiveTask, MavenArtifact> {
        private ArchiveTaskNotationParser() {
            super(AbstractArchiveTask.class);
        }

        @Override
        protected MavenArtifact parseType(AbstractArchiveTask archiveTask) {
            DefaultMavenArtifact artifact = instantiator.newInstance(DefaultMavenArtifact.class, archiveTask.getArchivePath(), archiveTask.getExtension(), archiveTask.getClassifier());
            artifact.builtBy(archiveTask);
            return artifact;
        }
    }

    private class PublishArtifactNotationParser extends TypedNotationParser<PublishArtifact, MavenArtifact> {
        private PublishArtifactNotationParser() {
            super(PublishArtifact.class);
        }

        @Override
        protected MavenArtifact parseType(PublishArtifact publishArtifact) {
            return instantiator.newInstance(PublishArtifactMavenArtifact.class, publishArtifact);
        }
    }

    private class FileNotationParser implements NotationParser<MavenArtifact> {
        private final FileResolver fileResolver;

        private FileNotationParser(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        public MavenArtifact parseNotation(Object notation) throws UnsupportedNotationException {
            File file = fileResolver.resolve(notation);
            return parseFile(file);
        }

        protected MavenArtifact parseFile(File file) {
            ArtifactFile artifactFile = new ArtifactFile(file, version);
            return instantiator.newInstance(DefaultMavenArtifact.class, file, artifactFile.getExtension(), artifactFile.getClassifier());
        }

        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Anything that can be converted to a file, as per Project.file()");
        }
    }

    private class MavenArtifactMapNotationParser extends MapNotationParser<MavenArtifact> {
        private final NotationParser<MavenArtifact> sourceNotationParser;
        private final NotationParser<MavenArtifact> fileNotationParser;

        private MavenArtifactMapNotationParser(NotationParser<MavenArtifact> sourceNotationParser, NotationParser<MavenArtifact> fileNotationParser) {
            this.sourceNotationParser = sourceNotationParser;
            this.fileNotationParser = fileNotationParser;
        }

        protected MavenArtifact parseMap(@MapKey("source") @Optional Object source, @MapKey("file") @Optional Object file) {
            if (source != null && file == null) {
                return sourceNotationParser.parseNotation(source);
            }
            if (file != null && source == null) {
                return fileNotationParser.parseNotation(file);
            }
            throw new InvalidUserDataException("Must supply exactly one of the following keys: [source, file]");
        }

        @Override
        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Maps containing either a 'file' or a 'source' entry, e.g. [file: '/path/to/file', extension: 'zip'].");
        }
    }
}
