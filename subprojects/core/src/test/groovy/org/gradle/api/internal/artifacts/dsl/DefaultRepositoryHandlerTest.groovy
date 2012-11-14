/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.ThreadGlobalInstantiator
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainerTest
import org.gradle.api.internal.artifacts.repositories.FixedResolverArtifactRepository
import org.gradle.internal.reflect.Instantiator
import org.junit.Test

class DefaultRepositoryHandlerTest extends DefaultArtifactRepositoryContainerTest {

    RepositoryFactoryInternal repositoryFactory
    DefaultRepositoryHandler handler

    def setup() {
        repositoryFactory = Mock(RepositoryFactoryInternal)
        repositoryFactory.getBaseRepositoryFactory() >> baseRepositoryFactory
        handler = createRepositoryHandler()
    }

    public ArtifactRepositoryContainer createRepositoryHandler(
            RepositoryFactoryInternal repositoryFactory = repositoryFactory,
            Instantiator instantiator = ThreadGlobalInstantiator.getOrCreate()
    ) {
        new DefaultRepositoryHandler(repositoryFactory, instantiator)
    }

    def testFlatDirWithClosure() {
        given:
        def repository = Mock(TestFlatDirectoryArtifactRepository)
        1 * repositoryFactory.flatDir(_ as Action) >> repository

        expect:
        handler.flatDir { name = 'libs' }.is(repository)
    }

    def testFlatDirWithMap() {
        given:
        def repository = Mock(TestFlatDirectoryArtifactRepository)
        1 * repositoryFactory.flatDir(_ as Map) >> repository

        expect:
        handler.flatDir([name: 'libs'] + [dirs: ['a', 'b']]).is(repository)
    }

    public void testMavenCentralWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        1 * repositoryFactory.mavenCentral() >> repository
        repository.getName() >> "name"

        then:
        handler.mavenCentral().is(repository)
    }

    public void testMavenCentralWithMap() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        1 * repositoryFactory.mavenCentral() >> repository
        1 * repository.setArtifactUrls(["abc"])
        repository.getName() >> "name"

        then:
        handler.mavenCentral(artifactUrls: ["abc"]).is(repository)
    }

    def testMavenLocalWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        1 * repositoryFactory.mavenLocal() >> repository
        repository.getName() >> "name"

        then:
        handler.mavenLocal().is(repository)
    }

    def testMavenRepoWithNameAndUrls() {
        when:
        String testUrl1 = 'http://www.gradle1.org'
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'

        TestMavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        repositoryFactory.maven(_ as Action) >> repository
        1 * repository.setName(repoName)
        repository.getName() >> repoName
        1 * repository.setUrl(repoRoot)
        1 * repository.setArtifactUrls([testUrl1, testUrl2])
        DependencyResolver resolver = new FileSystemResolver(name: "resolver")
        1 * baseRepositoryFactory.toResolver(repository) >> resolver

        then:
        handler.mavenRepo([name: repoName, url: repoRoot, artifactUrls: [testUrl1, testUrl2]]).is(resolver)
        handler.size() == 1
        handler.first() instanceof FixedResolverArtifactRepository
        handler.first().createResolver() == resolver
    }

    @Test
    public void testMavenRepoWithNameAndRootUrlOnly() {
        when:
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'

        TestMavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        repositoryFactory.maven(_ as Action) >> repository
        1 * repository.setName(repoName)
        repository.getName() >> repoName
        1 * repository.setUrl(repoRoot)
        DependencyResolver resolver = new FileSystemResolver(name: "resolver")
        1 * baseRepositoryFactory.toResolver(repository) >> resolver

        then:
        handler.mavenRepo([name: repoName, url: repoRoot]).is(resolver)
        handler.size() == 1
        handler.first().createResolver() == resolver
    }

    @Test
    public void testMavenRepoWithoutName() {
        when:
        String repoRoot = 'http://www.reporoot.org'

        TestMavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        repositoryFactory.maven(_ as Action) >> repository
        repository.getName() >> null
        1 * repository.setUrl(repoRoot)
        DependencyResolver resolver = new FileSystemResolver(name: "resolver")
        1 * baseRepositoryFactory.toResolver(repository) >> resolver

        then:
        handler.mavenRepo([url: repoRoot]).is(resolver)
        handler.size() == 1
        handler.first().createResolver() == resolver
    }

    public void createIvyRepositoryUsingClosure() {
        when:
        def repository = Mock(TestIvyArtifactRepository)
        1 * repositoryFactory.ivy(_ as Action) >> repository

        then:
        handler.ivy { }.is repository
    }

    def createIvyRepositoryUsingAction() {
        when:
        def repository = Mock(TestIvyArtifactRepository)
        def action = Mock(Action)
        1 * repositoryFactory.ivy(action) >> repository

        then:
        handler.ivy(action).is repository
    }

    @Test
    public void providesADefaultNameForIvyRepository() {
        given:
        def repo1 = Mock(TestIvyArtifactRepository)
        def repo1Name = "ivy"
        repo1.getName() >> { repo1Name }
        repo1.setName(_) >> { repo1Name = it[0] }

        def repo2 = Mock(TestIvyArtifactRepository)
        def repo2Name = "ivy"
        repo2.getName() >> { repo2Name }
        repo2.setName(_) >> { repo2Name = it[0] }

        def repo3 = Mock(TestIvyArtifactRepository)
        def repo3Name = "ivy"
        repo3.getName() >> { repo3Name }
        repo3.setName(_) >> { repo3Name = it[0] }

        repositoryFactory.ivy(_) >>> [repo1, repo2, repo3]

        when:
        handler.ivy { }
        handler.ivy { }
        handler.ivy { }

        then:
        repo1Name == "ivy"
        repo2Name == "ivy2"
        repo3Name == "ivy3"
    }

    public void createMavenRepositoryUsingClosure() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        1 * repositoryFactory.maven(_ as Action) >> repository

        then:
        handler.maven { }.is repository
    }

    public void createMavenRepositoryUsingAction() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        def action = Mock(Action)
        1 * repositoryFactory.maven(action) >> repository

        then:
        handler.maven(action).is repository
    }

}


