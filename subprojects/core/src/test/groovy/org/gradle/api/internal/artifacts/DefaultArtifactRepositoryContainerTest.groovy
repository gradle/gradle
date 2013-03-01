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

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.UnknownRepositoryException
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultArtifactRepositoryContainerTest extends Specification {

    BaseRepositoryFactory repositoryFactory
    DefaultArtifactRepositoryContainer container

    def setup() {
        repositoryFactory = Mock(BaseRepositoryFactory)
        container = createResolverContainer()
    }

    ArtifactRepositoryContainer createResolverContainer(
            BaseRepositoryFactory repositoryFactory = repositoryFactory,
            Instantiator instantiator = new DirectInstantiator()
    ) {
        new DefaultArtifactRepositoryContainer(repositoryFactory, instantiator)
    }

    List setupNotation(int i, repositoryFactory = repositoryFactory) {
        setupNotation("repoNotation$i", i == 1 ? "repository" : "repository${i-1}", "resolver$i", repositoryFactory)
    }

    List setupNotation(notation, repoName, resolverName, repositoryFactory = repositoryFactory) {
        def repo = Mock(ArtifactRepositoryInternal) { getName() >> repoName }
        def resolver = Mock(DependencyResolver)
        def resolverRepo = Mock(ArtifactRepositoryInternal)

        interaction {
            1 * repositoryFactory.createRepository(notation) >> repo
            1 * repositoryFactory.toResolver(repo) >> resolver
            1 * repositoryFactory.createResolverBackedRepository(resolver) >> resolverRepo
            1 * resolverRepo.setName(repoName)
            _ * resolverRepo.getName() >> repoName
            1 * resolverRepo.onAddToContainer(container)
        }

        [notation, repo, resolver, resolverRepo]
    }

    def "can add resolver"() {
        given:
        def (repo1Notation, repo1, resolver1, resolverRepo1) = setupNotation(1)
        def (repo2Notation, repo2, resolver2, resolverRepo2) = setupNotation(2)

        expect:
        container.addLast(repo1Notation).is resolver1
        assert container.findByName("repository") != null
        container.addLast(repo2Notation)
        container == [resolverRepo1, resolverRepo2]
    }

    def "can add repositories with duplicate names"() {
        given:
        def (repo1Notation, repo1, resolver1, resolverRepo1) = setupNotation(1)
        def (repo2Notation, repo2, resolver2, resolverRepo2) = setupNotation(2)

        when:
        container.addLast(repo1Notation)
        container.addLast(repo2Notation)

        then:
        container*.name == ["repository", "repository1"]
    }

    def testAddResolverWithClosure() {
        given:
        def repo = Mock(ArtifactRepositoryInternal) { getName() >> "name" }
        def resolver = Mock(DependencyResolver)
        def resolverRepo = Mock(ArtifactRepositoryInternal)

        interaction {
            1 * repositoryFactory.createRepository(resolver) >> repo
            1 * repositoryFactory.toResolver(repo) >> resolver
            1 * repositoryFactory.createResolverBackedRepository(resolver) >> resolverRepo
            _ * resolverRepo.name >> "bar"
            1 * resolverRepo.onAddToContainer(container)
        }

        when:
        container.add(resolver) {
            name = "bar"
        }

        then:
        1 * resolver.setName("bar")
    }

    def testAddBefore() {
        given:
        def (repo1Notation, repo1, resolver1, resolverRepo1) = setupNotation(1)
        def (repo2Notation, repo2, resolver2, resolverRepo2) = setupNotation(2)

        when:
        container.addLast(repo1Notation)
        container.addBefore(repo2Notation, "repository")

        then:
        container == [resolverRepo2, resolverRepo1]
    }

    def testAddAfter() {
        given:
        def (repo1Notation, repo1, resolver1, resolverRepo1) = setupNotation(1)
        def (repo2Notation, repo2, resolver2, resolverRepo2) = setupNotation(2)
        def (repo3Notation, repo3, resolver3, resolverRepo3) = setupNotation(3)

        when:
        container.addLast(repo1Notation)
        container.addAfter(repo2Notation, "repository")
        container.addAfter(repo3Notation, "repository")

        then:
        container == [resolverRepo1, resolverRepo3, resolverRepo2]
    }


    def testAddBeforeWithUnknownResolver() {
        when:
        container.addBefore("asdfasd", 'unknownName')

        then:
        thrown(UnknownRepositoryException)
    }

    def testAddAfterWithUnknownResolver() {
        when:
        container.addAfter("asdfasd", 'unknownName')

        then:
        thrown(UnknownRepositoryException)
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

    def testAddFirstUsingUserDescription() {
        given:
        def (repo1Notation, repo1, resolver1, resolverRepo1) = setupNotation(1)
        def (repo2Notation, repo2, resolver2, resolverRepo2) = setupNotation(2)

        when:
        container.addFirst(repo1Notation)
        container.addFirst(repo2Notation)

        then:
        container == [resolverRepo2, resolverRepo1]
    }

    def testAddLastUsingUserDescription() {
        given:
        def (repo1Notation, repo1, resolver1, resolverRepo1) = setupNotation(1)
        def (repo2Notation, repo2, resolver2, resolverRepo2) = setupNotation(2)

        when:
        container.addLast(repo1Notation)
        container.addLast(repo2Notation)

        then:
        container == [resolverRepo1, resolverRepo2]
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
        ArtifactRepository repository = Mock(ArtifactRepository)

        when:
        container.all(action)
        container.add(repository)

        then:
        1 * action.execute(repository)
    }

    def notificationsAreFiredWhenRepositoryIsAddedToTheHead() {
        Action<ArtifactRepository> action = Mock(Action)
        ArtifactRepository repository = Mock(ArtifactRepository)

        when:
        container.all(action)
        container.addFirst(repository)

        then:
        1 * action.execute(repository)
    }

    def notificationsAreFiredWhenRepositoryIsAddedToTheTail() {
        Action<ArtifactRepository> action = Mock(Action)
        ArtifactRepository repository = Mock(ArtifactRepository)

        when:
        container.all(action)
        container.addLast(repository)

        then:
        1 * action.execute(repository)
    }

}
