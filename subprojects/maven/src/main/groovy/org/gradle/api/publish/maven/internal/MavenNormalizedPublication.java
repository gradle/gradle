/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.publish.maven.MavenArtifact;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class MavenNormalizedPublication implements MavenProjectIdentity {

    private final MavenProjectIdentity projectIdentity;
    private final Set<MavenArtifact> artifacts;
    private final Set<Dependency> runtimeDependencies;
    private final Action<XmlProvider> pomWithXmlAction;

    public MavenNormalizedPublication(MavenProjectIdentity projectIdentity, Set<MavenArtifact> artifacts, Set<Dependency> runtimeDependencies, Action<XmlProvider> pomWithXmlAction) {
        this.projectIdentity = projectIdentity;
        this.runtimeDependencies = runtimeDependencies;
        this.pomWithXmlAction = pomWithXmlAction;
        this.artifacts = artifacts;
    }

    public String getArtifactId() {
        return projectIdentity.getArtifactId();
    }

    public String getGroupId() {
        return projectIdentity.getGroupId();
    }

    public String getVersion() {
        return projectIdentity.getVersion();
    }

    public String getPackaging() {
        if (projectIdentity.getPackaging() != null) {
            return projectIdentity.getPackaging();
        }
        return getMainArtifact() == null ? "pom" : getMainArtifact().getExtension();
    }

    public MavenArtifact getMainArtifact() {
        for (MavenArtifact artifact : artifacts) {
            if (artifact.getClassifier() == null || artifact.getClassifier().length() == 0) {
                return artifact;
            }
        }
        return null;
    }

    public Set<MavenArtifact> getAdditionalArtifacts() {
        if (artifacts.isEmpty()) {
            return Collections.emptySet();
        }
        Set<MavenArtifact> additionalArtifacts = new LinkedHashSet<MavenArtifact>(artifacts);
        additionalArtifacts.remove(getMainArtifact());
        return additionalArtifacts;
    }

    public Set<MavenArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<Dependency> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public Action<XmlProvider> getPomWithXmlAction() {
        return pomWithXmlAction;
    }
}
