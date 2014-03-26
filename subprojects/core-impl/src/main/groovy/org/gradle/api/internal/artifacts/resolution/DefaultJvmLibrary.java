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
import org.gradle.api.artifacts.resolution.*;

public class DefaultJvmLibrary extends AbstractSoftwareComponent<JvmLibraryArtifact> implements JvmLibrary {
    private final SoftwareArtifactSet<JvmLibrarySourcesArtifact> sourceArtifacts;
    private final SoftwareArtifactSet<JvmLibraryJavadocArtifact> javadocArtifacts;

    public DefaultJvmLibrary(ComponentIdentifier componentId,
                             SoftwareArtifactSet<JvmLibrarySourcesArtifact> sourceArtifacts,
                             SoftwareArtifactSet<JvmLibraryJavadocArtifact> javadocArtifacts) {
        super(componentId);
        this.sourceArtifacts = sourceArtifacts;
        this.javadocArtifacts = javadocArtifacts;
    }

    public SoftwareArtifactSet<JvmLibrarySourcesArtifact> getSourcesArtifacts() {
        return sourceArtifacts;
    }

    public SoftwareArtifactSet<JvmLibraryJavadocArtifact> getJavadocArtifacts() {
        return javadocArtifacts;
    }
}
