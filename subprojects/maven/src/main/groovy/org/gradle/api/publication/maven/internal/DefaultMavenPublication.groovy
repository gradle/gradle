/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.publication.maven.internal

import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.MavenArtifact
import org.gradle.api.publication.maven.MavenDependency
import org.gradle.api.publication.maven.MavenPom

class DefaultMavenPublication implements MavenPublication {
    String groupId
    String artifactId
    String version
    String packaging
    MavenArtifact mainArtifact
    Set<MavenArtifact> subArtifacts = []
    Set<MavenDependency> dependencies = []
    MavenPom pom
}
