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
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.artifacts.publish.DecoratingPublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.Factory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypedNotationConverter;

import java.io.File;

public class PublishArtifactNotationParserFactory implements Factory<NotationParser<Object, ConfigurablePublishArtifact>> {
    private final Instantiator instantiator;
    private final DependencyMetaDataProvider metaDataProvider;
    private final TaskResolver taskResolver;

    public PublishArtifactNotationParserFactory(Instantiator instantiator, DependencyMetaDataProvider metaDataProvider, TaskResolver taskResolver) {
        this.instantiator = instantiator;
        this.metaDataProvider = metaDataProvider;
        this.taskResolver = taskResolver;
    }

    @Override
    public NotationParser<Object, ConfigurablePublishArtifact> create() {
        FileNotationConverter fileConverter = new FileNotationConverter();
        return NotationParserBuilder
                .toType(ConfigurablePublishArtifact.class)
                .converter(new DecoratingConverter())
                .converter(new ArchiveTaskNotationConverter())
                .converter(new FileProviderNotationConverter())
                .converter(new FileSystemLocationNotationConverter())
                .converter(fileConverter)
                .converter(new FileMapNotationConverter(fileConverter))
                .toComposite();
    }

    private class DecoratingConverter extends TypedNotationConverter<PublishArtifact, ConfigurablePublishArtifact> {
        private DecoratingConverter() {
            super(PublishArtifact.class);
        }

        @Override
        protected ConfigurablePublishArtifact parseType(PublishArtifact notation) {
            return instantiator.newInstance(DecoratingPublishArtifact.class, notation);
        }
    }

    private class ArchiveTaskNotationConverter extends TypedNotationConverter<AbstractArchiveTask, ConfigurablePublishArtifact> {
        private ArchiveTaskNotationConverter() {
            super(AbstractArchiveTask.class);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of AbstractArchiveTask").example("jar");
        }

        @Override
        protected ConfigurablePublishArtifact parseType(AbstractArchiveTask notation) {
            return instantiator.newInstance(ArchivePublishArtifact.class, notation);
        }
    }

    private static class FileMapNotationConverter extends MapNotationConverter<ConfigurablePublishArtifact> {
        private final FileNotationConverter fileConverter;

        private FileMapNotationConverter(FileNotationConverter fileConverter) {
            this.fileConverter = fileConverter;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Maps with 'file' key");
        }

        protected PublishArtifact parseMap(@MapKey("file") File file) {
            return fileConverter.parseType(file);
        }
    }

    private class FileProviderNotationConverter extends TypedNotationConverter<Provider<?>, ConfigurablePublishArtifact> {
        @SuppressWarnings("unchecked")
        FileProviderNotationConverter() {
            super((Class)Provider.class);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of Provider<RegularFile>.");
            visitor.candidate("Instances of Provider<Directory>.");
            visitor.candidate("Instances of Provider<File>.");
        }

        @Override
        protected ConfigurablePublishArtifact parseType(Provider<?> notation) {
            Module module = metaDataProvider.getModule();
            return instantiator.newInstance(DecoratingPublishArtifact.class, new LazyPublishArtifact(notation, module.getVersion()));
        }
    }

    private class FileSystemLocationNotationConverter extends TypedNotationConverter<FileSystemLocation, ConfigurablePublishArtifact> {
        FileSystemLocationNotationConverter() {
            super(FileSystemLocation.class);
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.candidate("Instances of RegularFile.");
            visitor.candidate("Instances of Directory.");
        }

        @Override
        protected ConfigurablePublishArtifact parseType(FileSystemLocation notation) {
            Module module = metaDataProvider.getModule();
            return instantiator.newInstance(DecoratingPublishArtifact.class, new FileSystemPublishArtifact(notation, module.getVersion()));
        }
    }

    private class FileNotationConverter extends TypedNotationConverter<File, ConfigurablePublishArtifact> {
        private FileNotationConverter() {
            super(File.class);
        }

        @Override
        protected ConfigurablePublishArtifact parseType(File file) {
            Module module = metaDataProvider.getModule();
            ArtifactFile artifactFile = new ArtifactFile(file, module.getVersion());
            return instantiator.newInstance(DefaultPublishArtifact.class, taskResolver, artifactFile.getName(), artifactFile.getExtension(), artifactFile.getExtension(), artifactFile.getClassifier(), null, file, new Task[0]);
        }
    }
}
