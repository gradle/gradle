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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenArtifactSet;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.artifact.DefaultMavenArtifactSet;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultMavenPublication implements MavenPublicationInternal {

    private final String name;
    private final MavenPomInternal pom;
    private final MavenProjectIdentity projectIdentity;
    private final File pomDir;
    private final DefaultMavenArtifactSet mavenArtifacts;
    private SoftwareComponentInternal component;

    public DefaultMavenPublication(
            String name, MavenPomInternal mavenPom, MavenProjectIdentity projectIdentity, File pomDir, NotationParser<MavenArtifact> mavenArtifactParser
    ) {
        this.name = name;
        this.pom = mavenPom;
        this.projectIdentity = projectIdentity;
        this.pomDir = pomDir;
        mavenArtifacts = new DefaultMavenArtifactSet(mavenArtifactParser);
    }

    public String getName() {
        return name;
    }

    public MavenPomInternal getPom() {
        return pom;
    }

    public void pom(Action<? super MavenPom> configure) {
        configure.execute(pom);
    }

    public void from(SoftwareComponent component) {
        if (this.component != null) {
            throw new InvalidUserDataException("A MavenPublication cannot include multiple components");
        }
        this.component = (SoftwareComponentInternal) component;

        // TODO:DAZ This isn't lazy enough. Entire PublishArtifactSet should be in MavenArtifactSet, preferably.
        for (PublishArtifact publishArtifact : this.component.getArtifacts()) {
            artifact(publishArtifact);
        }
    }

    public MavenArtifact artifact(Object source) {
        return mavenArtifacts.addArtifact(source);
    }

    public MavenArtifact artifact(Object source, Action<MavenArtifact> config) {
        return mavenArtifacts.addArtifact(source, config);
    }

    public MavenArtifactSet getArtifacts() {
        return mavenArtifacts;
    }

    public FileCollection getPublishableFiles() {
        return mavenArtifacts.getFiles();
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        Set<Dependency> runtimeDependencies = component == null ? Collections.<Dependency>emptySet() : component.getRuntimeDependencies();
        MavenNormalizedPublication normalizedPublication = new MavenNormalizedPublication(projectIdentity, mavenArtifacts, runtimeDependencies, pom.getXmlAction());
        normalizedPublication.validate();
        return normalizedPublication;
    }

    public File getPomDir() {
        return pomDir;
    }
}
