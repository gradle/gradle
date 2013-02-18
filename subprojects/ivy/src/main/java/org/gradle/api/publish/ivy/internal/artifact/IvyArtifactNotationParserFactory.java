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

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.dsl.ArtifactFile;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.UnsupportedNotationException;
import org.gradle.api.internal.notations.parsers.MapKey;
import org.gradle.api.internal.notations.parsers.MapNotationParser;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Collection;

public class IvyArtifactNotationParserFactory implements Factory<NotationParser<IvyArtifact>> {
    private final Instantiator instantiator;
    private final String version;
    private final FileResolver fileResolver;

    public IvyArtifactNotationParserFactory(Instantiator instantiator, String version, FileResolver fileResolver) {
        this.instantiator = instantiator;
        // TODO:DAZ Remove the use of version in parsing artifact names
        this.version = version;
        this.fileResolver = fileResolver;
    }

    public NotationParser<IvyArtifact> create() {
        FileNotationParser fileNotationParser = new FileNotationParser(fileResolver);
        ArchiveTaskNotationParser archiveTaskNotationParser = new ArchiveTaskNotationParser();
        PublishArtifactNotationParser publishArtifactNotationParser = new PublishArtifactNotationParser();

        NotationParser<IvyArtifact> sourceNotationParser = new NotationParserBuilder<IvyArtifact>()
                .resultingType(IvyArtifact.class)
                .parser(archiveTaskNotationParser)
                .parser(publishArtifactNotationParser)
                .parser(fileNotationParser)
                .toComposite();

        IvyArtifactMapNotationParser ivyArtifactMapNotationParser = new IvyArtifactMapNotationParser(sourceNotationParser);

        NotationParserBuilder<IvyArtifact> parserBuilder = new NotationParserBuilder<IvyArtifact>()
                .resultingType(IvyArtifact.class)
                .parser(archiveTaskNotationParser)
                .parser(publishArtifactNotationParser)
                .parser(ivyArtifactMapNotationParser)
                .parser(fileNotationParser);

        return parserBuilder.toComposite();
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
        private final NotationParser<File> fileResolverNotationParser;

        private FileNotationParser(FileResolver fileResolver) {
            this.fileResolverNotationParser = fileResolver.asNotationParser();
        }

        public IvyArtifact parseNotation(Object notation) throws UnsupportedNotationException {
            File file = fileResolverNotationParser.parseNotation(notation);
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
            fileResolverNotationParser.describe(candidateFormats);
        }
    }

    private class IvyArtifactMapNotationParser extends MapNotationParser<IvyArtifact> {
        private final NotationParser<IvyArtifact> sourceNotationParser;

        private IvyArtifactMapNotationParser(NotationParser<IvyArtifact> sourceNotationParser) {
            this.sourceNotationParser = sourceNotationParser;
        }

        protected IvyArtifact parseMap(@MapKey("source") Object source) {
            return sourceNotationParser.parseNotation(source);
        }

        @Override
        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Maps containing a 'source' entry, e.g. [source: '/path/to/file', extension: 'zip'].");
        }
    }

    private static String emptyToNull(String value) {
        return GUtil.elvis(value, null);
    }

}
