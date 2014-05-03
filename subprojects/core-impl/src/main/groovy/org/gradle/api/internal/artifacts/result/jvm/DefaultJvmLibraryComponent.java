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
package org.gradle.api.internal.artifacts.result.jvm;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.Component;
import org.gradle.api.artifacts.result.jvm.JavadocArtifact;
import org.gradle.api.artifacts.result.jvm.JvmLibraryComponent;
import org.gradle.api.artifacts.result.jvm.SourcesArtifact;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultJvmLibraryComponent implements JvmLibraryComponent, Component {
    private final ComponentIdentifier componentId;
    private final Set<SourcesArtifact> sourceArtifacts;
    private final Set<JavadocArtifact> javadocArtifacts;

    public DefaultJvmLibraryComponent(ComponentIdentifier componentId,
                                      Set<? extends SourcesArtifact> sourceArtifacts,
                                      Set<? extends JavadocArtifact> javadocArtifacts) {
        this.componentId = componentId;
        this.sourceArtifacts = new LinkedHashSet<SourcesArtifact>(sourceArtifacts);
        this.javadocArtifacts = new LinkedHashSet<JavadocArtifact>(javadocArtifacts);
    }

    public ComponentIdentifier getId() {
        return componentId;
    }

    public Set<SourcesArtifact> getSourcesArtifacts() {
        return sourceArtifacts;
    }

    public Set<JavadocArtifact> getJavadocArtifacts() {
        return javadocArtifacts;
    }
}
