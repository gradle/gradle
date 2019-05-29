/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publisher

import com.google.common.collect.Sets
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptySet
import static java.util.Collections.unmodifiableSet

class FilteringMavenPublisherTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()

    def delegate = Mock(MavenPublisher)
    def publisher = new FilteringMavenPublisher(delegate)
    def repository = Stub(MavenArtifactRepository)

    def "delegates accept the same properties except allArtifacts"() {
        def publication = createPublication()

        when:
        publisher.publish(publication, repository)

        then:
        1 * delegate.publish({ p ->
            p.name == publication.name
            p.projectIdentity == publication.projectIdentity
            p.groupId == publication.groupId
            p.artifactId == publication.artifactId
            p.version == publication.version
            p.packaging == publication.packaging
            p.pomArtifact == publication.pomArtifact
            p.mainArtifact == publication.mainArtifact
            p.allArtifacts == emptySet()
        }, repository)
        1 * publication.mainArtifact.ignoreIfAbsent >> false
        _ * publication.pomArtifact.ignoreIfAbsent >> false
        0 * _._
    }

    def "artifact is ignored when it is optional and not exists"() {
        def a = artifact(false, false)
        def b = artifact(true, false)
        def c = artifact(false, true)
        def d = artifact(true, true)

        when:
        publisher.publish(createPublication(a, b, c, d), repository)

        then:
        1 * delegate.publish({
            it.allArtifacts == [a, c, d].toSet()
        }, repository)
        0 * _._
    }

    private def artifact(boolean ignoreIfAbsent, boolean exist) {
        return Stub(MavenArtifact) {
            getFile() >> {
                exist ? testDir.createFile(System.currentTimeMillis()) : testDir.file(System.currentTimeMillis())
            }
            getIgnoreIfAbsent() >> ignoreIfAbsent
        }
    }

    private def createPublication() {
        return new DefaultMavenNormalizedPublication("test-name", Stub(MavenProjectIdentity), "pom", Mock(MavenArtifact), Mock(MavenArtifact), emptySet())
    }

    private def createPublication(MavenArtifact... artifacts) {
        return new DefaultMavenNormalizedPublication("test-name", Stub(MavenProjectIdentity), "pom", Mock(MavenArtifact), Mock(MavenArtifact), unmodifiableSet(Sets.newHashSet(artifacts)))
    }
}
