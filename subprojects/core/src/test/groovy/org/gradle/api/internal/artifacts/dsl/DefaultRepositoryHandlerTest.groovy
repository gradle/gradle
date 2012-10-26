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
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainerTest
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.assertEquals

/**
 * @author Hans Dockter
 */
@RunWith(JMock)
class DefaultRepositoryHandlerTest extends DefaultArtifactRepositoryContainerTest {
    private DefaultRepositoryHandler repositoryHandler

    public ArtifactRepositoryContainer createResolverContainer() {
        repositoryHandler = new DefaultRepositoryHandler(baseRepositoryFactoryMock, new DirectInstantiator());
        return repositoryHandler;
    }

    @Test public void testFlatDirWithClosure() {
        def repository = context.mock(FlatDirectoryArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setName('libs')
            allowing(repository).getName(); will(returnValue('libs'))
        }

        assert repositoryHandler.flatDir { name = 'libs' }.is(repository)
    }
    
    @Test public void testFlatDirWithNameAndDirs() {
        def repository = context.mock(FlatDirectoryArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setDirs(['a', 'b'])
            one(repository).setName('libs')
            allowing(repository).getName(); will(returnValue('libs'))
        }

        assert repositoryHandler.flatDir([name: 'libs'] + [dirs: ['a', 'b']]).is(repository)
    }

    @Test public void testFlatDirWithNameAndSingleDir() {
        def repository = context.mock(FlatDirectoryArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setDirs(['a'])
            one(repository).setName('libs')
            allowing(repository).getName(); will(returnValue('libs'))
        }

        assert repositoryHandler.flatDir([name: 'libs'] + [dirs: 'a']).is(repository)
    }

    @Test public void testFlatDirWithoutNameAndWithDirs() {
        def repository = context.mock(FlatDirectoryArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setDirs(['a', 12])
            one(repository).getName(); will(returnValue(null))
            one(repository).setName('flatDir')
            allowing(repository).getName(); will(returnValue('flatDir'))
        }

        assert repositoryHandler.flatDir([dirs: ['a', 12]]).is(repository)
    }

    @Test
    public void testMavenCentralWithNoArgs() {
        MavenArtifactRepository repository = context.mock(MavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenCentralRepository()
            will(returnValue(repository))
            one(repository).getName()
            will(returnValue(null))
            one(repository).setName(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME)
            allowing(repository).getName()
            will(returnValue(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME))
        }

        assert repositoryHandler.mavenCentral().is(repository)
    }

    @Test
    public void testMavenCentralWithSingleUrl() {
        String testUrl2 = 'http://www.gradle2.org'

        MavenArtifactRepository repository = context.mock(MavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenCentralRepository()
            will(returnValue(repository))
            one(repository).getName()
            will(returnValue(null))
            one(repository).setName(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME)
            allowing(repository).getName()
            will(returnValue(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME))
            one(repository).setArtifactUrls([testUrl2])
        }

        assert repositoryHandler.mavenCentral(artifactUrls: [testUrl2]).is(repository)
    }

    @Test
    public void testMavenCentralWithNameAndUrls() {
        String testUrl1 = 'http://www.gradle1.org'
        String testUrl2 = 'http://www.gradle2.org'
        String name = 'customName'

        MavenArtifactRepository repository = context.mock(MavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenCentralRepository()
            will(returnValue(repository))
            one(repository).setName('customName')
            allowing(repository).getName()
            will(returnValue('customName'))
            one(repository).setArtifactUrls([testUrl1, testUrl2])
        }

        assert repositoryHandler.mavenCentral(name: name, artifactUrls: [testUrl1, testUrl2]).is(repository)
    }

    @Test
    public void testMavenLocalWithNoArgs() {
        MavenArtifactRepository repository = context.mock(MavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenLocalRepository()
            will(returnValue(repository))
            one(repository).getName()
            will(returnValue(null))
            one(repository).setName(ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME)
            allowing(repository).getName()
            will(returnValue(ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME))
        }

        assert repositoryHandler.mavenLocal() == repository
    }

    @Test
    public void testMavenRepoWithNameAndUrls() {
        String testUrl1 = 'http://www.gradle1.org'
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(repository).setName(repoName)
            allowing(repository).getName()
            will(returnValue(repoName))
            one(repository).setUrl(repoRoot)
            one(repository).setArtifactUrls([testUrl1, testUrl2])
            allowing(repository).createResolver(); will(returnValue(expectedResolver))
        }

        assert repositoryHandler.mavenRepo([name: repoName, url: repoRoot, artifactUrls: [testUrl1, testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithNameAndRootUrlOnly() {
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(repository).setName(repoName)
            allowing(repository).getName()
            will(returnValue(repoName))
            one(repository).setUrl(repoRoot)
            allowing(repository).createResolver(); will(returnValue(expectedResolver))
        }

        assert repositoryHandler.mavenRepo([name: repoName, url: repoRoot]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithoutName() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue(null))
            one(repository).setUrl(repoRoot)
            one(repository).setArtifactUrls([testUrl2])
            allowing(repository).createResolver(); will(returnValue(expectedResolver))
        }

        assert repositoryHandler.mavenRepo([url: repoRoot, artifactUrls: [testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void createIvyRepositoryUsingClosure() {
        IvyArtifactRepository repository = context.mock(IvyArtifactRepository.class)

        context.checking {
            one(baseRepositoryFactoryMock).createIvyRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def arg
        def result = repositoryHandler.ivy {
            arg = it
        }

        assert arg == repository
        assert result == repository
    }

    @Test
    public void createIvyRepositoryUsingAction() {
        IvyArtifactRepository repository = context.mock(IvyArtifactRepository.class)
        Action<IvyArtifactRepository> action = context.mock(Action.class)

        context.checking {
            one(baseRepositoryFactoryMock).createIvyRepository()
            will(returnValue(repository))
            one(action).execute(repository)
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def result = repositoryHandler.ivy(action)
        assert result == repository
    }

    @Test
    public void providesADefaultNameForIvyRepository() {
        IvyArtifactRepository repository1 = context.mock(IvyArtifactRepository.class)

        context.checking {
            one(baseRepositoryFactoryMock).createIvyRepository()
            will(returnValue(repository1))
            one(repository1).getName()
            will(returnValue(null))
            one(repository1).setName("ivy")
            allowing(repository1).getName()
            will(returnValue("ivy"))
        }

        repositoryHandler.ivy { }

        IvyArtifactRepository repository2 = context.mock(IvyArtifactRepository.class)

        context.checking {
            one(baseRepositoryFactoryMock).createIvyRepository()
            will(returnValue(repository2))
            allowing(repository2).getName()
            will(returnValue("ivy2"))
        }

        repositoryHandler.ivy { }

        IvyArtifactRepository repository3 = context.mock(IvyArtifactRepository.class)

        context.checking {
            one(baseRepositoryFactoryMock).createIvyRepository()
            will(returnValue(repository3))
            one(repository3).getName()
            will(returnValue(null))
            one(repository3).setName("ivy3")
            allowing(repository3).getName()
            will(returnValue("ivy3"))
        }

        repositoryHandler.ivy { }
    }

    @Test
    public void createMavenRepositoryUsingClosure() {
        MavenArtifactRepository repository = context.mock(TestMavenArtifactRepository.class)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def arg
        def result = repositoryHandler.maven {
            arg = it
        }

        assert arg == repository
        assert result == repository
    }

    @Test
    public void createMavenRepositoryUsingAction() {
        MavenArtifactRepository repository = context.mock(TestMavenArtifactRepository.class)
        Action<MavenArtifactRepository> action = context.mock(Action.class)

        context.checking {
            one(baseRepositoryFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(action).execute(repository)
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def result = repositoryHandler.maven(action)
        assert result == repository
    }

    private DependencyResolver resolver(String name = 'name') {
        DependencyResolver resolver = context.mock(DependencyResolver.class)
        context.checking {
            allowing(resolver).getName(); will(returnValue(name))
        }
        return resolver
    }

    private void prepareName(mavenResolver, String expectedName) {
        context.checking {
            one(mavenResolver).setName(expectedName)
        }
    }
}

interface TestMavenArtifactRepository extends MavenArtifactRepository, ArtifactRepositoryInternal {
}

interface TestFlatDirectoryArtifactRepository extends FlatDirectoryArtifactRepository, ArtifactRepositoryInternal {
}
