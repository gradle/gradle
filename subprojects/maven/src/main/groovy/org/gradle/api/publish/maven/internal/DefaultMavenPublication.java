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
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collections;

public class DefaultMavenPublication implements MavenPublicationInternal {

    private final String name;
    private final MavenPomInternal pom;
    private final MavenProjectIdentity projectIdentity;
    private final File pomDir;
    private SoftwareComponentInternal component;

    public DefaultMavenPublication(
            String name, Instantiator instantiator, MavenProjectIdentity projectIdentity, File pomDir
    ) {
        this.name = name;
        this.pom = instantiator.newInstance(DefaultMavenPom.class);
        this.projectIdentity = projectIdentity;
        this.pomDir = pomDir;
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
    }

    public FileCollection getPublishableFiles() {
        if (component == null) {
            return new SimpleFileCollection();
        }
        return component.getArtifacts().getFiles();
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        if (component == null) {
            return new MavenNormalizedPublication(projectIdentity, Collections.<PublishArtifact>emptySet(), Collections.<Dependency>emptySet(), pom.getXmlAction());
        }
        return new MavenNormalizedPublication(projectIdentity, component.getArtifacts(), component.getRuntimeDependencies(), pom.getXmlAction());
    }

    public File getPomDir() {
        return pomDir;
    }
}
