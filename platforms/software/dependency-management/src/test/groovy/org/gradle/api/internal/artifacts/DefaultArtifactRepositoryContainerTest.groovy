/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.UnknownRepositoryException
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultArtifactRepositoryContainerTest extends Specification {
    DefaultArtifactRepositoryContainer container

    def setup() {
        container = createResolverContainer()
    }

    ArtifactRepositoryContainer createResolverContainer(Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()) {
        new DefaultArtifactRepositoryContainer(instantiator, CollectionCallbackActionDecorator.NOOP)
    }

    def testAddFirst() {
        given:
        def repo1 = Mock(ArtifactRepository) { getName() >> "a" }
        def repo2 = Mock(ArtifactRepository) { getName() >> "b" }

        when:
        container.addFirst(repo1)
        container.addFirst(repo2)

        then:
        container == [repo2, repo1]
        container.collect { it } == [repo2, repo1]
        container.matching { true } == [repo2, repo1]
        container.matching { true }.collect { it } == [repo2, repo1]
    }

    def testAddLast() {
        given:
        def repo1 = Mock(ArtifactRepository) { getName() >> "a" }
        def repo2 = Mock(ArtifactRepository) { getName() >> "b" }

        when:
        container.addLast(repo1)
        container.addLast(repo2)

        then:
        container == [repo1, repo2]
    }

    def testGetThrowsExceptionForUnknownResolver() {
        when:
        container.getByName("unknown")

        then:
        def e = thrown(UnknownRepositoryException)
        e.message == "Repository with name 'unknown' not found."
    }

    def notificationsAreFiredWhenRepositoryIsAdded() {
        Action<ArtifactRepository> action = Mock(Action)
        ArtifactRepository repository = Mock(ArtifactRepository) { getName() >> "name" }

        when:
        container.all(action)
        container.add(repository)

        then:
        1 * action.execute(repository)
    }

    def notificationsAreFiredWhenRepositoryIsAddedToTheHead() {
        Action<ArtifactRepository> action = Mock(Action)
        ArtifactRepository repository = Mock(ArtifactRepository) { getName() >> "name" }

        when:
        container.all(action)
        container.addFirst(repository)

        then:
        1 * action.execute(repository)
    }

    def notificationsAreFiredWhenRepositoryIsAddedToTheTail() {
        Action<ArtifactRepository> action = Mock(Action)
        ArtifactRepository repository = Mock(ArtifactRepository) { getName() >> "name" }

        when:
        container.all(action)
        container.addLast(repository)

        then:
        1 * action.execute(repository)
    }

}
