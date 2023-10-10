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

import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainerTest
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil

class DefaultRepositoryHandlerTest extends DefaultArtifactRepositoryContainerTest {
    BaseRepositoryFactory repositoryFactory = Mock()
    DefaultRepositoryHandler handler

    def setup() {
        handler = createRepositoryHandler()
    }

    ArtifactRepositoryContainer createRepositoryHandler(
        BaseRepositoryFactory repositoryFactory = repositoryFactory,
        Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    ) {
        new DefaultRepositoryHandler(repositoryFactory, instantiator, CollectionCallbackActionDecorator.NOOP)
    }

    def testFlatDirWithClosure() {
        given:
        def repository = Mock(TestFlatDirectoryArtifactRepository) { getName() >> "name" }
        1 * repositoryFactory.createFlatDirRepository() >> repository

        expect:
        handler.flatDir { name = 'libs' }.is(repository)
    }

    def testFlatDirWithMap() {
        given:
        def repository = Mock(TestFlatDirectoryArtifactRepository) { getName() >> "name" }
        1 * repositoryFactory.createFlatDirRepository() >> repository

        expect:
        handler.flatDir([name: 'libs'] + [dirs: ['a', 'b']]).is(repository)
    }

    public void testMavenCentralWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository) { getName() >> "name" }
        1 * repositoryFactory.createMavenCentralRepository() >> repository
        repository.getName() >> "name"

        then:
        handler.mavenCentral().is(repository)
    }

    public void testMavenCentralWithMap() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository) { getName() >> "name" }
        1 * repositoryFactory.createMavenCentralRepository() >> repository
        1 * repository.setArtifactUrls(["abc"])
        repository.getName() >> "name"

        then:
        handler.mavenCentral(artifactUrls: ["abc"]).is(repository)
    }

    def testMavenLocalWithNoArgs() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository)
        1 * repositoryFactory.createMavenLocalRepository() >> repository
        repository.getName() >> "name"

        then:
        handler.mavenLocal().is(repository)
    }

    public void createIvyRepositoryUsingClosure() {
        when:
        def repository = Mock(TestIvyArtifactRepository) { getName() >> "name" }
        1 * repositoryFactory.createIvyRepository() >> repository

        then:
        handler.ivy {}.is repository
    }

    def createIvyRepositoryUsingAction() {
        when:
        def repository = Mock(TestIvyArtifactRepository) { getName() >> "name" }
        def action = Mock(Action)
        1 * repositoryFactory.createIvyRepository() >> repository

        then:
        handler.ivy(action).is repository
    }

    void providesADefaultNameForIvyRepository() {
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

        repositoryFactory.createIvyRepository() >>> [repo1, repo2, repo3]

        when:
        handler.ivy {}
        handler.ivy {}
        handler.ivy {}

        then:
        repo1Name == "ivy"
        repo2Name == "ivy2"
        repo3Name == "ivy3"
    }

    public void createMavenRepositoryUsingClosure() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository) { getName() >> "name" }
        1 * repositoryFactory.createMavenRepository() >> repository

        then:
        handler.maven {}.is repository
    }

    public void createMavenRepositoryUsingAction() {
        when:
        MavenArtifactRepository repository = Mock(TestMavenArtifactRepository) { getName() >> "name" }
        def action = Mock(Action)
        1 * repositoryFactory.createMavenRepository() >> repository

        then:
        handler.maven(action).is repository
    }

    def "can include group exclusively"() {
        given:
        def repo1 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 1" }
        def repo2 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 2" }
        def repo3 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 3" }
        def repo1Content = Mock(RepositoryContentDescriptor)
        def repo2Content = Mock(RepositoryContentDescriptor)
        def repo3Content = Mock(RepositoryContentDescriptor)

        when:
        handler.maven {}
        handler.maven {}
        handler.maven {}
        handler.exclusiveContent {
            it.forRepository { repo1 }
            it.filter {
                it.includeGroup("foo")
            }
        }

        then:
        3 * repositoryFactory.createMavenRepository() >>> [repo1, repo2, repo3]
        _ * repo1.getName() >> "Maven repo 1"
        _ * repo2.getName() >> "Maven repo 2"
        _ * repo3.getName() >> "Maven repo 3"
        _ * repo1.setName(_)
        _ * repo2.setName(_)
        _ * repo3.setName(_)
        1 * repo1.onAddToContainer(_)
        1 * repo2.onAddToContainer(_)
        1 * repo3.onAddToContainer(_)
        1 * repo1.content(_) >> { args ->
            args[0].execute(repo1Content)
        }
        1 * repo1Content.includeGroup("foo")
        1 * repo2.content(_) >> { args ->
            args[0].execute(repo2Content)
        }
        1 * repo2Content.excludeGroup("foo")
        1 * repo3.content(_) >> { args ->
            args[0].execute(repo3Content)
        }
        1 * repo3Content.excludeGroup("foo")
        0 * _
    }

    def "can include group by regex exclusively"() {
        given:
        def repo1 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 1" }
        def repo2 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 2" }
        def repo3 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 3" }
        def repo1Content = Mock(RepositoryContentDescriptor)
        def repo2Content = Mock(RepositoryContentDescriptor)
        def repo3Content = Mock(RepositoryContentDescriptor)

        when:
        handler.maven {}
        handler.maven {}
        handler.maven {}
        handler.exclusiveContent {
            it.forRepository { repo1 }
            it.filter {
                it.includeGroupByRegex("foo")
            }
        }

        then:
        3 * repositoryFactory.createMavenRepository() >>> [repo1, repo2, repo3]
        _ * repo1.getName() >> "Maven repo 1"
        _ * repo2.getName() >> "Maven repo 2"
        _ * repo3.getName() >> "Maven repo 3"
        _ * repo1.setName(_)
        _ * repo2.setName(_)
        _ * repo3.setName(_)
        1 * repo1.onAddToContainer(_)
        1 * repo2.onAddToContainer(_)
        1 * repo3.onAddToContainer(_)
        1 * repo1.content(_) >> { args ->
            args[0].execute(repo1Content)
        }
        1 * repo1Content.includeGroupByRegex("foo")
        1 * repo2.content(_) >> { args ->
            args[0].execute(repo2Content)
        }
        1 * repo2Content.excludeGroupByRegex("foo")
        1 * repo3.content(_) >> { args ->
            args[0].execute(repo3Content)
        }
        1 * repo3Content.excludeGroupByRegex("foo")
        0 * _
    }

    def "can include module exclusively"() {
        given:
        def repo1 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 1" }
        def repo2 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 2" }
        def repo3 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 3" }
        def repo1Content = Mock(RepositoryContentDescriptor)
        def repo2Content = Mock(RepositoryContentDescriptor)
        def repo3Content = Mock(RepositoryContentDescriptor)

        when:
        handler.maven {}
        handler.maven {}
        handler.maven {}
        handler.exclusiveContent {
            it.forRepository { repo2 }
            it.filter {
                it.includeModule("com.mycompany", "core")
            }
        }

        then:
        3 * repositoryFactory.createMavenRepository() >>> [repo1, repo2, repo3]
        _ * repo1.getName() >> "Maven repo 1"
        _ * repo2.getName() >> "Maven repo 2"
        _ * repo3.getName() >> "Maven repo 3"
        _ * repo1.setName(_)
        _ * repo2.setName(_)
        _ * repo3.setName(_)
        1 * repo1.onAddToContainer(_)
        1 * repo2.onAddToContainer(_)
        1 * repo3.onAddToContainer(_)
        1 * repo2.content(_) >> { args ->
            args[0].execute(repo2Content)
        }
        1 * repo2Content.includeModule("com.mycompany", "core")
        1 * repo1.content(_) >> { args ->
            args[0].execute(repo1Content)
        }
        1 * repo1Content.excludeModule("com.mycompany", "core")
        1 * repo3.content(_) >> { args ->
            args[0].execute(repo3Content)
        }
        1 * repo3Content.excludeModule("com.mycompany","core")
        0 * _
    }

    def "can include module by regex exclusively"() {
        given:
        def repo1 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 1" }
        def repo2 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 2" }
        def repo3 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 3" }
        def repo1Content = Mock(RepositoryContentDescriptor)
        def repo2Content = Mock(RepositoryContentDescriptor)
        def repo3Content = Mock(RepositoryContentDescriptor)

        when:
        handler.maven {}
        handler.maven {}
        handler.maven {}
        handler.exclusiveContent {
            it.forRepository { repo2 }
            it.filter {
                it.includeModuleByRegex("com.mycompany", "core")
            }
        }

        then:
        3 * repositoryFactory.createMavenRepository() >>> [repo1, repo2, repo3]
        _ * repo1.getName() >> "Maven repo 1"
        _ * repo2.getName() >> "Maven repo 2"
        _ * repo3.getName() >> "Maven repo 3"
        _ * repo1.setName(_)
        _ * repo2.setName(_)
        _ * repo3.setName(_)
        1 * repo1.onAddToContainer(_)
        1 * repo2.onAddToContainer(_)
        1 * repo3.onAddToContainer(_)
        1 * repo2.content(_) >> { args ->
            args[0].execute(repo2Content)
        }
        1 * repo2Content.includeModuleByRegex("com.mycompany", "core")
        1 * repo1.content(_) >> { args ->
            args[0].execute(repo1Content)
        }
        1 * repo1Content.excludeModuleByRegex("com.mycompany", "core")
        1 * repo3.content(_) >> { args ->
            args[0].execute(repo3Content)
        }
        1 * repo3Content.excludeModuleByRegex("com.mycompany","core")
        0 * _
    }

    def "can include module version exclusively"() {
        given:
        def repo1 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 1" }
        def repo2 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 2" }
        def repo3 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 3" }
        def repo1Content = Mock(RepositoryContentDescriptor)
        def repo2Content = Mock(RepositoryContentDescriptor)
        def repo3Content = Mock(RepositoryContentDescriptor)

        when:
        handler.maven {}
        handler.maven {}
        handler.maven {}
        handler.exclusiveContent {
            it.forRepository { repo2 }
            it.filter {
                it.includeVersion("com.mycompany", "core", "1.0")
            }
        }

        then:
        3 * repositoryFactory.createMavenRepository() >>> [repo1, repo2, repo3]
        _ * repo1.getName() >> "Maven repo 1"
        _ * repo2.getName() >> "Maven repo 2"
        _ * repo3.getName() >> "Maven repo 3"
        _ * repo1.setName(_)
        _ * repo2.setName(_)
        _ * repo3.setName(_)
        1 * repo1.onAddToContainer(_)
        1 * repo2.onAddToContainer(_)
        1 * repo3.onAddToContainer(_)
        1 * repo2.content(_) >> { args ->
            args[0].execute(repo2Content)
        }
        1 * repo2Content.includeVersion("com.mycompany", "core", "1.0")
        1 * repo1.content(_) >> { args ->
            args[0].execute(repo1Content)
        }
        1 * repo1Content.excludeVersion("com.mycompany", "core", "1.0")
        1 * repo3.content(_) >> { args ->
            args[0].execute(repo3Content)
        }
        1 * repo3Content.excludeVersion("com.mycompany","core", "1.0")
        0 * _
    }

    def "can include module version by regex exclusively"() {
        given:
        def repo1 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 1" }
        def repo2 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 2" }
        def repo3 = Mock(TestMavenArtifactRepository) { getName() >> "Maven repo 3" }
        def repo1Content = Mock(RepositoryContentDescriptor)
        def repo2Content = Mock(RepositoryContentDescriptor)
        def repo3Content = Mock(RepositoryContentDescriptor)

        when:
        handler.maven {}
        handler.maven {}
        handler.maven {}
        handler.exclusiveContent {
            it.forRepository { repo2 }
            it.filter {
                it.includeVersionByRegex("com.mycompany", "core", "1.0")
            }
        }

        then:
        3 * repositoryFactory.createMavenRepository() >>> [repo1, repo2, repo3]
        _ * repo1.getName() >> "Maven repo 1"
        _ * repo2.getName() >> "Maven repo 2"
        _ * repo3.getName() >> "Maven repo 3"
        _ * repo1.setName(_)
        _ * repo2.setName(_)
        _ * repo3.setName(_)
        1 * repo1.onAddToContainer(_)
        1 * repo2.onAddToContainer(_)
        1 * repo3.onAddToContainer(_)
        1 * repo2.content(_) >> { args ->
            args[0].execute(repo2Content)
        }
        1 * repo2Content.includeVersionByRegex("com.mycompany", "core", "1.0")
        1 * repo1.content(_) >> { args ->
            args[0].execute(repo1Content)
        }
        1 * repo1Content.excludeVersionByRegex("com.mycompany", "core", "1.0")
        1 * repo3.content(_) >> { args ->
            args[0].execute(repo3Content)
        }
        1 * repo3Content.excludeVersionByRegex("com.mycompany","core", "1.0")
        0 * _
    }
}


