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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.dsl.ArtifactFile;
import org.gradle.api.internal.notations.NotationParserBuilder;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.internal.notations.api.TopLevelNotationParser;
import org.gradle.api.internal.notations.api.UnsupportedNotationException;
import org.gradle.api.internal.notations.parsers.MapKey;
import org.gradle.api.internal.notations.parsers.MapNotationParser;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collection;

public class IvyArtifactNotationParser implements NotationParser<IvyArtifact>, TopLevelNotationParser {
    private final Instantiator instantiator;
    private final Module module;
    private final NotationParser<IvyArtifact> delegate;

    public IvyArtifactNotationParser(Instantiator instantiator, Module module, Project project) {
        this.instantiator = instantiator;
        this.module = module;
        FileNotationParser fileNotationParser = new FileNotationParser(project);
        NotationParserBuilder<IvyArtifact> parserBuilder = new NotationParserBuilder<IvyArtifact>()
                .resultingType(IvyArtifact.class)
                .parser(new ArchiveTaskNotationParser())
                .parser(new PublishArtifactNotationParser())
                .parser(new FileMapNotationParser(fileNotationParser))
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
        protected IvyArtifact parseType(AbstractArchiveTask notation) {
            return instantiator.newInstance(ArchiveIvyArtifact.class, notation);
        }
    }

    private class PublishArtifactNotationParser extends TypedNotationParser<PublishArtifact, IvyArtifact> {
        private PublishArtifactNotationParser() {
            super(PublishArtifact.class);
        }

        @Override
        protected IvyArtifact parseType(PublishArtifact notation) {
            return instantiator.newInstance(PublishArtifactIvyArtifact.class, notation);
        }
    }

    private class FileMapNotationParser extends MapNotationParser<IvyArtifact> {
        private final FileNotationParser fileParser;

        private FileMapNotationParser(FileNotationParser fileParser) {
            this.fileParser = fileParser;
        }

        protected IvyArtifact parseMap(@MapKey("file") Object file) {
            return fileParser.parseNotation(file);
        }
    }

    private class FileNotationParser implements NotationParser<IvyArtifact> {
        private final Project project;

        private FileNotationParser(Project project) {
            this.project = project;
        }

        public IvyArtifact parseNotation(Object notation) throws UnsupportedNotationException {
            File file = toProjectFile(notation);
            return parseFile(file);
        }

        private File toProjectFile(Object notation) {
            try {
                return project.file(notation);
            } catch (Exception e) {
                throw new UnsupportedNotationException(notation);
            }
        }

        protected IvyArtifact parseFile(File file) {
            ArtifactFile artifactFile = new ArtifactFile(file, module);
            return instantiator.newInstance(FileIvyArtifact.class, file, artifactFile.getName(), artifactFile.getClassifier(), artifactFile.getExtension());
        }

        public void describe(Collection<String> candidateFormats) {
            candidateFormats.add("Anything that can be converted to a file, as per Project.file()");
        }
    }
}
