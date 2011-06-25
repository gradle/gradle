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
import org.apache.maven.model.Model
import org.apache.maven.model.Dependency
import org.gradle.api.publication.maven.MavenDependency

class MavenPublicationPomGenerator {
    private final MavenPublication publication

    private final Model model = new Model()

    MavenPublicationPomGenerator(MavenPublication publication) {
        this.publication = publication
    }

    Model generatePom() {
        generateMainAttributes()
        generateDependencies()
        model
    }

    private void generateMainAttributes() {
        model.modelVersion = publication.modelVersion
        model.groupId = publication.groupId
        model.artifactId = publication.artifactId
        model.version = publication.version
        model.packaging = publication.packaging
        model.description = publication.description
    }

    private void generateDependencies() {
        model.dependencies = publication.dependencies.collect { MavenDependency mavenDep ->
            def dependency = new Dependency()
            dependency.groupId = mavenDep.groupId
            dependency.artifactId = mavenDep.artifactId
            dependency.version = mavenDep.version
            dependency.classifier = mavenDep.classifier
            dependency.scope = mavenDep.scope.name().toLowerCase()
            dependency.optional = mavenDep.optional
        }
    }
}
