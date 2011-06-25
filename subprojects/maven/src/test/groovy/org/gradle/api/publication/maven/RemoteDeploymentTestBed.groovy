/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.publication.maven.internal.DefaultMavenPublisher
import org.gradle.api.publication.maven.internal.model.DefaultMavenArtifact
import org.gradle.api.publication.maven.internal.model.DefaultMavenAuthentication
import org.gradle.api.publication.maven.internal.model.DefaultMavenPublication
import org.gradle.api.publication.maven.internal.model.DefaultMavenRepository
import org.sonatype.aether.repository.LocalRepository

def dir = "/Users/szczepan/gradle/sf-tests/maven-hacks"

def publisher = new DefaultMavenPublisher(new LocalRepository("$dir/repository"))

def repo = new DefaultMavenRepository(url: "http://repo.gradle.org/gradle/integ-tests")
repo.authentication = new DefaultMavenAuthentication(userName: 'szczepiq', password: 'secret')

def publication = new DefaultMavenPublication(groupId: "gradleware.test", artifactId: "fooArtifact", version: "1.1")
def artifact = new DefaultMavenArtifact(classifier: "", extension: "jar", file: new File("$dir/foo.jar"))
publication.mainArtifact = artifact

publisher.install(publication)
publisher.deploy(publication, repo)
