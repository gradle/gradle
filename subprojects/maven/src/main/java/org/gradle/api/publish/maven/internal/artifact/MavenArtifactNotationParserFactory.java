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

package org.gradle.api.publish.maven.internal.artifact;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Factory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.*;

import java.io.File;

public class MavenArtifactNotationParserFactory implements Factory<NotationParser<Object, MavenArtifact>> {
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    public MavenArtifactNotationParserFactory(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    public NotationParser<Object, MavenArtifact> create() {
        FileNotationConverter fileNotationConverter = new FileNotationConverter(fileResolver);
        ArchiveTaskNotationConverter archiveTaskNotationConverter = new ArchiveTaskNotationConverter();
        PublishArtifactNotationConverter publishArtifactNotationConverter = new PublishArtifactNotationConverter();

        NotationParser<Object, MavenArtifact> sourceNotationParser = NotationParserBuilder
                .toType(MavenArtifact.class)
                .fromType(AbstractArchiveTask.class, archiveTaskNotationConverter)
                .fromType(PublishArtifact.class, publishArtifactNotationConverter)
                .converter(fileNotationConverter)
                .toComposite();

        MavenArtifactMapNotationConverter mavenArtifactMapNotationConverter = new MavenArtifactMapNotationConverter(sourceNotationParser);

        NotationParserBuilder<MavenArtifact> parserBuilder = NotationParserBuilder
                .toType(MavenArtifact.class)
                .fromType(AbstractArchiveTask.class, archiveTaskNotationConverter)
                .fromType(PublishArtifact.class, publishArtifactNotationConverter)
                .converter(mavenArtifactMapNotationConverter)
                .converter(fileNotationConverter);

        return parserBuilder.toComposite();
    }

    private class ArchiveTaskNotationConverter implements NotationConverter<AbstractArchiveTask, MavenArtifact> {
        public void convert(AbstractArchiveTask archiveTask, NotationConvertResult<? super MavenArtifact> result) throws TypeConversionException {
            DefaultMavenArtifact artifact = instantiator.newInstance(
                    DefaultMavenArtifact.class,
                    archiveTask.getArchivePath(), archiveTask.getExtension(), archiveTask.getClassifier());
            artifact.builtBy(archiveTask);
            result.converted(artifact);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of AbstractArchiveTask").example("jar");
        }
    }

    private class PublishArtifactNotationConverter implements NotationConverter<PublishArtifact, MavenArtifact> {
        public void convert(PublishArtifact publishArtifact, NotationConvertResult<? super MavenArtifact> result) throws TypeConversionException {
            DefaultMavenArtifact artifact = instantiator.newInstance(
                    DefaultMavenArtifact.class,
                    publishArtifact.getFile(), publishArtifact.getExtension(), publishArtifact.getClassifier());
            artifact.builtBy(publishArtifact.getBuildDependencies());
            result.converted(artifact);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of PublishArtifact");
        }
    }

    private class FileNotationConverter implements NotationConverter<Object, MavenArtifact> {
        private final NotationParser<Object, File> fileResolverNotationParser;

        private FileNotationConverter(FileResolver fileResolver) {
            this.fileResolverNotationParser = fileResolver.asNotationParser();
        }

        public void convert(Object notation, NotationConvertResult<? super MavenArtifact> result) throws TypeConversionException {
            File file = fileResolverNotationParser.parseNotation(notation);
            result.converted(parseFile(file));
        }

        protected MavenArtifact parseFile(File file) {
            String extension = StringUtils.substringAfterLast(file.getName(), ".");
            return instantiator.newInstance(DefaultMavenArtifact.class, file, extension, null);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            fileResolverNotationParser.describe(visitor);
        }
    }

    private class MavenArtifactMapNotationConverter extends MapNotationConverter<MavenArtifact> {
        private final NotationParser<Object, MavenArtifact> sourceNotationParser;

        private MavenArtifactMapNotationConverter(NotationParser<Object, MavenArtifact> sourceNotationParser) {
            this.sourceNotationParser = sourceNotationParser;
        }

        protected MavenArtifact parseMap(@MapKey("source") Object source) {
            return sourceNotationParser.parseNotation(source);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps containing a 'source' entry").example("[source: '/path/to/file', extension: 'zip']");
        }
    }
}
