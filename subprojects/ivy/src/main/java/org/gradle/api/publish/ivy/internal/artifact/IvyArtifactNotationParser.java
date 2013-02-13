/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.artifact;

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
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Collection;

public class IvyArtifactNotationParser implements NotationParser<IvyArtifact>, TopLevelNotationParser {
    private final Instantiator instantiator;
    private final String version;
    private final NotationParser<IvyArtifact> delegate;

    public IvyArtifactNotationParser(Instantiator instantiator, String version, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.version = version;
        FileNotationParser fileNotationParser = new FileNotationParser(fileResolver);
        ArchiveTaskNotationParser archiveTaskNotationParser = new ArchiveTaskNotationParser();
        PublishArtifactNotationParser publishArtifactNotationParser = new PublishArtifactNotationParser();

        NotationParser<IvyArtifact> sourceNotationParser = new NotationParserBuilder<IvyArtifact>()
                .resultingType(IvyArtifact.class)
                .parser(archiveTaskNotationParser)
                .parser(publishArtifactNotationParser)
                .parser(fileNotationParser)
                .toComposite();

        IvyArtifactMapNotationParser ivyArtifactMapNotationParser = new IvyArtifactMapNotationParser(sourceNotationParser, fileNotationParser);

        NotationParserBuilder<IvyArtifact> parserBuilder = new NotationParserBuilder<IvyArtifact>()
                .resultingType(IvyArtifact.class)
                .parser(archiveTaskNotationParser)
                .parser(publishArtifactNotationParser)
                .parser(ivyArtifactMapNotationParser)
                .parser(fileNotationParser);

        delegate = parserBuilder.toComposite();
    }

    public void describe(Collection<String> candidateFormats) {
        delegate.describe(candidateFormats);
    }

    public IvyArtifact parseNotation(Object notation) {
        return delegate.parseNotation(notation);
    }

    private class ArchiveTaskNotationParser extends TypedNotationParser<AbstractArchiveTask, IvyArtifact> {
        private ArchiveTaskNotationParser() {
            super(AbstractArchiveTask.class);
        }

        @Override
        protected IvyArtifact parseType(AbstractArchiveTask archiveTask) {
            DefaultIvyArtifact ivyArtifact = instantiator.newInstance(
                    DefaultIvyArtifact.class,
                    archiveTask.getArchivePath(), archiveTask.getBaseName(), GUtil.elvis(archiveTask.getExtension(), null), archiveTask.getExtension(), GUtil.elvis(archiveTask.getClassifier(), null)
            );
            ivyArtifact.builtBy(archiveTask);
            return ivyArtifact;
        }
    }

    private class PublishArtifactNotationParser extends TypedNotationParser<PublishArtifact, IvyArtifact> {
        private PublishArtifactNotationParser() {
            super(PublishArtifact.class);
        }

        @Override
        protected IvyArtifact parseType(PublishArtifact publishArtifact) {
            DefaultIvyArtifact ivyArtifact = instantiator.newInstance(
                    DefaultIvyArtifact.class,
                    publishArtifact.getFile(), publishArtifact.getName(), emptyToNull(publishArtifact.getExtension()), emptyToNull(publishArtifact.getType()), emptyToNull(publishArtifact.getClassifier())
            );
            ivyArtifact.builtBy(publishArtifact.getBuildDependencies());
            return ivyArtifact;
        }
    }

    private class FileNotationParser implements NotationParser<IvyArtifact> {
        private final FileResolver fileResolver;

        private FileNotationParser(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        public IvyArtifact parseNotation(Object notation) throws UnsupportedNotationException {
            File file = fileResolver.resolve(notation);
            return parseFile(file);
        }

        protected IvyArtifact parseFile(File file) {
            ArtifactFile artifactFile = new ArtifactFile(file, version);
            return instantiator.newInstance(
                    DefaultIvyArtifact.class, file,
                    artifactFile.getName(), artifactFile.getExtension(), artifactFile.getExtension(), artifactFile.getClassifier()
            );
        }

        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Anything that can be converted to a file, as per Project.file()");
        }
    }


    private class IvyArtifactMapNotationParser extends MapNotationParser<IvyArtifact> {
        private final NotationParser<IvyArtifact> sourceNotationParser;
        private final NotationParser<IvyArtifact> fileNotationParser;

        private IvyArtifactMapNotationParser(NotationParser<IvyArtifact> sourceNotationParser, NotationParser<IvyArtifact> fileNotationParser) {
            this.sourceNotationParser = sourceNotationParser;
            this.fileNotationParser = fileNotationParser;
        }

        protected IvyArtifact parseMap(@MapKey("source") @Optional Object source, @MapKey("file") @Optional Object file) {
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

    private static String emptyToNull(String value) {
        return GUtil.elvis(value, null);
    }

}
