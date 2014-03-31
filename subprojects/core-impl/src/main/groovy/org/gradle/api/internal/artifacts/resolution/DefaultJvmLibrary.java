/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.resolution;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.resolution.JvmLibrary;
import org.gradle.api.artifacts.resolution.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.resolution.JvmLibrarySourcesArtifact;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultJvmLibrary extends AbstractSoftwareComponent implements JvmLibrary {
    private final Set<JvmLibrarySourcesArtifact> sourceArtifacts;
    private final Set<JvmLibraryJavadocArtifact> javadocArtifacts;

    public DefaultJvmLibrary(ComponentIdentifier componentId,
                             Set<? extends JvmLibrarySourcesArtifact> sourceArtifacts,
                             Set<? extends JvmLibraryJavadocArtifact> javadocArtifacts) {
        super(componentId);
        this.sourceArtifacts = new LinkedHashSet<JvmLibrarySourcesArtifact>(sourceArtifacts);
        this.javadocArtifacts = new LinkedHashSet<JvmLibraryJavadocArtifact>(javadocArtifacts);
    }

    public Set<JvmLibrarySourcesArtifact> getSourcesArtifacts() {
        return sourceArtifacts;
    }

    public Set<JvmLibraryJavadocArtifact> getJavadocArtifacts() {
        return javadocArtifacts;
    }
}
