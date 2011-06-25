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
package org.gradle.api.publication.maven

import org.gradle.api.publication.maven.internal.DefaultMavenPublisher
import org.gradle.api.publication.maven.internal.model.DefaultMavenArtifact
import org.gradle.api.publication.maven.internal.model.DefaultMavenPublication
import org.gradle.api.publication.maven.internal.model.DefaultMavenRepository

def publisher = new DefaultMavenPublisher()
def repo = new DefaultMavenRepository(url: "file:///swd/tmp/m2remoteRepo")
def publication = new DefaultMavenPublication(groupId: "mygroup", artifactId: "myartifact", version: "1.1")
def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: new File("/swd/tmp/fatjar/fatjar.jar"))
publication.mainArtifact = artifact

publisher.install(publication)
publisher.deploy(publication, repo)


