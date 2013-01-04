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
import org.gradle.api.artifacts.PublishArtifact;

public class MavenNormalizedPublication implements MavenProjectIdentity {

    private final MavenProjectIdentity projectIdentity;
    private final Iterable<PublishArtifact> artifacts;
    private final Iterable<Dependency> runtimeDependencies;
    private final Action<XmlProvider> pomWithXmlAction;

    public MavenNormalizedPublication(MavenProjectIdentity projectIdentity, Iterable<PublishArtifact> artifacts, Iterable<Dependency> runtimeDependencies, Action<XmlProvider> pomWithXmlAction) {
        this.projectIdentity = projectIdentity;
        this.artifacts = artifacts;
        this.runtimeDependencies = runtimeDependencies;
        this.pomWithXmlAction = pomWithXmlAction;
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
        return projectIdentity.getPackaging();
    }

    public Iterable<PublishArtifact> getArtifacts() {
        return artifacts;
    }

    public Iterable<Dependency> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public Action<XmlProvider> getPomWithXmlAction() {
        return pomWithXmlAction;
    }
}
