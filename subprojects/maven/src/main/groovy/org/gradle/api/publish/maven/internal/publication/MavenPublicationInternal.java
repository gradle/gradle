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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.file.FileCollection;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;

import java.util.Set;

public interface MavenPublicationInternal extends MavenPublication, PublicationInternal {

    MavenPomInternal getPom();

    void setPomFile(FileCollection pomFile);

    FileCollection getPublishableFiles();

    MavenProjectIdentity getMavenProjectIdentity();

    Set<MavenDependencyInternal> getRuntimeDependencies();

    MavenNormalizedPublication asNormalisedPublication();

    // TODO:DAZ Remove this attempt to guess packaging from artifacts. Packaging should come from component, or be explicitly set.
    String determinePackagingFromArtifacts();

}

