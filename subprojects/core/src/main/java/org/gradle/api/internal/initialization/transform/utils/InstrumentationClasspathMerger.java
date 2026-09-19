/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.utils;

import com.google.common.collect.Ordering;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.internal.instrumentation.reporting.MethodInterceptionReportCollector.INTERCEPTED_METHODS_REPORT_FILE;

public class InstrumentationClasspathMerger {

    public enum FileType {
        INTERCEPTED_METHODS_REPORT,
        ARTIFACT
    }

    /**
     * Merges external dependencies and project dependencies to one classpath that is sorted based on the original classpath.
     *
     * Returns a Map where the key is the {@link FileType} and the value is a list of files of that type.
     */
    public static Map<FileType, List<File>> mergeToClasspath(
        ArtifactCollection originalDependencies,
        ArtifactCollection externalDependencies,
        ArtifactCollection projectDependencies
    ) {
        List<ComponentArtifactIdentifier> identifiers = originalDependencies.getArtifacts().stream()
            .map(InstrumentationClasspathMerger::rootArtifactIdOf)
            // In some cases we end up with the same artifact multiple times in different locations,
            // additional user's artifact transform can be injected in between and could produce multiple artifacts from one original artifact.
            .distinct()
            .collect(Collectors.toList());

        Ordering<ComponentArtifactIdentifier> ordering = Ordering.explicit(identifiers);
        return Stream.concat(externalDependencies.getArtifacts().stream(), projectDependencies.getArtifacts().stream())
            .map(ClassPathTransformedArtifact::ofTransformedArtifact)
            // We sort based on the original classpath to we keep the original order,
            // we also rely on the fact that for ordered streams `sorted()` method has stable sort.
            .sorted((first, second) -> ordering.compare(first.originalIdentifier, second.originalIdentifier))
            .map(artifact -> artifact.file)
            .collect(Collectors.groupingBy(InstrumentationClasspathMerger::getFileType));
    }

    private static FileType getFileType(File file) {
        return file.getName().equals(INTERCEPTED_METHODS_REPORT_FILE) ? FileType.INTERCEPTED_METHODS_REPORT : FileType.ARTIFACT;
    }

    private static ComponentArtifactIdentifier rootArtifactIdOf(ResolvedArtifactResult artifact) {
        ComponentArtifactIdentifier id = artifact.getId();
        while (id instanceof TransformedComponentFileArtifactIdentifier) {
            id = ((TransformedComponentFileArtifactIdentifier) id).getInputArtifactId();
        }
        return id;
    }

    private static class ClassPathTransformedArtifact {
        private final File file;
        private final ComponentArtifactIdentifier originalIdentifier;

        private ClassPathTransformedArtifact(File file, ComponentArtifactIdentifier originalIdentifier) {
            this.file = file;
            this.originalIdentifier = originalIdentifier;
        }

        public static ClassPathTransformedArtifact ofTransformedArtifact(ResolvedArtifactResult transformedArtifact) {
            checkArgument(transformedArtifact.getId() instanceof TransformedComponentFileArtifactIdentifier);
            return new ClassPathTransformedArtifact(transformedArtifact.getFile(), rootArtifactIdOf(transformedArtifact));
        }

        @Override
        public String toString() {
            return "ClassPathTransformedArtifact{" +
                "file=" + file +
                ", originalIdentifier=" + originalIdentifier +
                '}';
        }
    }
}
