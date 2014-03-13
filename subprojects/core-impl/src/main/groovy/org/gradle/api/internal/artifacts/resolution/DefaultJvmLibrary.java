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

import com.google.common.collect.Iterables;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.resolution.JvmLibrary;
import org.gradle.api.artifacts.resolution.JvmLibraryArtifact;
import org.gradle.api.artifacts.resolution.JvmLibraryJavadocArtifact;
import org.gradle.api.artifacts.resolution.JvmLibrarySourcesArtifact;

public class DefaultJvmLibrary extends AbstractSoftwareComponent<JvmLibraryArtifact> implements JvmLibrary {
    public DefaultJvmLibrary(ComponentIdentifier componentId, Iterable<JvmLibraryArtifact> artifacts) {
        super(componentId, artifacts);
    }

    public Iterable<JvmLibrarySourcesArtifact> getSourcesArtifacts() {
        return Iterables.filter(getAllArtifacts(), JvmLibrarySourcesArtifact.class);
    }

    public Iterable<JvmLibraryJavadocArtifact> getJavadocArtifacts() {
        return Iterables.filter(getAllArtifacts(), JvmLibraryJavadocArtifact.class);
    }
}
