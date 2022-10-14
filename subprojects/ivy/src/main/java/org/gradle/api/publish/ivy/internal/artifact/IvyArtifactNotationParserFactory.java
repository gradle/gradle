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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Factory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import java.io.File;

public class IvyArtifactNotationParserFactory implements Factory<NotationParser<Object, IvyArtifact>> {
    private final Instantiator instantiator;
    private final FileResolver fileResolver;
    private final IvyPublicationIdentity publicationIdentity;
    private final TaskDependencyFactory taskDependencyFactory;

    public IvyArtifactNotationParserFactory(Instantiator instantiator, FileResolver fileResolver, IvyPublicationIdentity publicationIdentity, TaskDependencyFactory taskDependencyFactory) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
        this.publicationIdentity = publicationIdentity;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    @Override
    public NotationParser<Object, IvyArtifact> create() {
        FileNotationConverter fileNotationConverter = new FileNotationConverter(fileResolver);
        ArchiveTaskNotationConverter archiveTaskNotationConverter = new ArchiveTaskNotationConverter();
        PublishArtifactNotationConverter publishArtifactNotationConverter = new PublishArtifactNotationConverter();

        NotationParser<Object, IvyArtifact> sourceNotationParser = NotationParserBuilder
                .toType(IvyArtifact.class)
                .converter(archiveTaskNotationConverter)
                .converter(publishArtifactNotationConverter)
                .converter(fileNotationConverter)
                .toComposite();

        IvyArtifactMapNotationConverter ivyArtifactMapNotationConverter = new IvyArtifactMapNotationConverter(sourceNotationParser);

        return NotationParserBuilder
                .toType(IvyArtifact.class)
                .converter(archiveTaskNotationConverter)
                .converter(publishArtifactNotationConverter)
                .converter(ivyArtifactMapNotationConverter)
                .converter(fileNotationConverter)
                .toComposite();
    }

    private class ArchiveTaskNotationConverter extends TypedNotationConverter<AbstractArchiveTask, IvyArtifact> {
        private ArchiveTaskNotationConverter() {
            super(AbstractArchiveTask.class);
        }

        @Override
        protected IvyArtifact parseType(AbstractArchiveTask archiveTask) {
            return instantiator.newInstance(ArchiveTaskBasedIvyArtifact.class, archiveTask, publicationIdentity, taskDependencyFactory);
        }
    }

    private class PublishArtifactNotationConverter extends TypedNotationConverter<PublishArtifact, IvyArtifact> {
        private PublishArtifactNotationConverter() {
            super(PublishArtifact.class);
        }

        @Override
        protected IvyArtifact parseType(PublishArtifact publishArtifact) {
            return instantiator.newInstance(PublishArtifactBasedIvyArtifact.class, publishArtifact, publicationIdentity, taskDependencyFactory);
        }
    }

    private class FileNotationConverter implements NotationConverter<Object, IvyArtifact> {
        private final NotationParser<Object, File> fileResolverNotationParser;

        private FileNotationConverter(FileResolver fileResolver) {
            this.fileResolverNotationParser = fileResolver.asNotationParser();
        }

        @Override
        public void convert(Object notation, NotationConvertResult<? super IvyArtifact> result) throws TypeConversionException {
            File file = fileResolverNotationParser.parseNotation(notation);
            IvyArtifact ivyArtifact = instantiator.newInstance(FileBasedIvyArtifact.class, file, publicationIdentity, taskDependencyFactory);
            if (notation instanceof TaskDependencyContainer) {
                TaskDependencyContainer taskDependencyContainer;
                if (notation instanceof Provider) {
                    // wrap to disable special handling of providers by DefaultTaskDependency in this case
                    // (workaround for https://github.com/gradle/gradle/issues/11054)
                    taskDependencyContainer = context -> context.add(notation);
                } else {
                    taskDependencyContainer = (TaskDependencyContainer) notation;
                }
                ivyArtifact.builtBy(taskDependencyContainer);
            }
            result.converted(ivyArtifact);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            fileResolverNotationParser.describe(visitor);
        }
    }

    private static class IvyArtifactMapNotationConverter extends MapNotationConverter<IvyArtifact> {
        private final NotationParser<Object, IvyArtifact> sourceNotationParser;

        private IvyArtifactMapNotationConverter(NotationParser<Object, IvyArtifact> sourceNotationParser) {
            this.sourceNotationParser = sourceNotationParser;
        }

        protected IvyArtifact parseMap(@MapKey("source") Object source) {
            return sourceNotationParser.parseNotation(source);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps containing a 'source' entry").example("[source: '/path/to/file', extension: 'zip']");
        }
    }
}
