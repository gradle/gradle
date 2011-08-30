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
import org.apache.ivy.plugins.resolver.ResolverSettings
import org.gradle.api.Action
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.artifacts.dsl.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.dsl.IvyArtifactRepository
import org.gradle.api.artifacts.dsl.MavenArtifactRepository
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.DirectInstantiator
import org.gradle.api.internal.artifacts.DefaultResolverContainerTest
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.hamcrest.Matchers
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.notNullValue
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertSame

/**
 * @author Hans Dockter
 */
@RunWith(JMock)
class DefaultRepositoryHandlerTest extends DefaultResolverContainerTest {
    static final String TEST_REPO_URL = 'http://www.gradle.org'

    private DefaultRepositoryHandler repositoryHandler

    public ResolverContainer createResolverContainer() {
        repositoryHandler = new DefaultRepositoryHandler(resolverFactoryMock, fileResolver, new DirectInstantiator());
        return repositoryHandler;
    }

    @Test public void testFlatDirWithClosure() {
        def repository = context.mock(TestFlatDirectoryArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setName('libs')
            allowing(repository).getName(); will(returnValue('libs'))
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.flatDir { name = 'libs' }.is(repository)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }
    
    @Test public void testFlatDirWithNameAndDirs() {
        def repository = context.mock(TestFlatDirectoryArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setDirs(['a', 'b'])
            one(repository).setName('libs')
            allowing(repository).getName(); will(returnValue('libs'))
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.flatDir([name: 'libs'] + [dirs: ['a', 'b']]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }

    @Test public void testFlatDirWithNameAndSingleDir() {
        def repository = context.mock(TestFlatDirectoryArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setDirs(['a'])
            one(repository).setName('libs')
            allowing(repository).getName(); will(returnValue('libs'))
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.flatDir([name: 'libs'] + [dirs: 'a']).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }

    @Test public void testFlatDirWithoutNameAndWithDirs() {
        def repository = context.mock(TestFlatDirectoryArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createFlatDirRepository(); will(returnValue(repository))
            one(repository).setDirs(['a', 12])
            allowing(repository).getName(); will(returnValue(null))
            one(repository).setName('flatDir')
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.flatDir([dirs: ['a', 12]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }

    @Test
    public void testMavenCentralWithNoArgs() {
        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenCentralRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue(null))
            one(repository).setName(ResolverContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME)
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenCentral().is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenCentralWithSingleUrl() {
        String testUrl2 = 'http://www.gradle2.org'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenCentralRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue(null))
            one(repository).setName(ResolverContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME)
            one(repository).setArtifactUrls([testUrl2])
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenCentral(urls: testUrl2).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenCentralWithNameAndUrls() {
        String testUrl1 = 'http://www.gradle1.org'
        String testUrl2 = 'http://www.gradle2.org'
        String name = 'customName'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenCentralRepository()
            will(returnValue(repository))
            one(repository).setName('customName')
            one(repository).getName()
            will(returnValue('customName'))
            one(repository).setArtifactUrls([testUrl1, testUrl2])
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenCentral(name: name, urls: [testUrl1, testUrl2]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenLocalWithNoArgs() {
        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenLocalRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue(null))
            one(repository).setName(ResolverContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME)
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenLocal() == expectedResolver
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithNameAndUrls() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(repository).setName(repoName)
            allowing(repository).getName()
            will(returnValue(repoName))
            one(repository).setUrl(repoRoot)
            one(repository).setArtifactUrls([testUrl2])
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenRepo([name: repoName, urls: [repoRoot, testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithNameAndRootUrlOnly() {
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(repository).setName(repoName)
            allowing(repository).getName()
            will(returnValue(repoName))
            one(repository).setUrl(repoRoot)
            one(repository).setArtifactUrls([])
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenRepo([name: repoName, urls: repoRoot]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithoutName() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'

        TestMavenArtifactRepository repository = context.mock(TestMavenArtifactRepository)

        context.checking {
            one(resolverFactoryMock).createMavenRepository()
            will(returnValue(repository))
            allowing(repository).getName()
            will(returnValue(null))
            one(repository).setName('maven')
            one(repository).setUrl(repoRoot)
            one(repository).setArtifactUrls([testUrl2])
            allowing(repository).createResolvers(withParam(notNullValue())); will { repos -> repos.add(expectedResolver) }
        }

        assert repositoryHandler.mavenRepo([urls: [repoRoot, testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void mavenDeployerWithoutName() {
        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
        String expectedName = RepositoryHandler.DEFAULT_MAVEN_DEPLOYER_NAME + "-" +
                System.identityHashCode(expectedResolver)
        prepareName(expectedResolver, expectedName)
        assertSame(expectedResolver, repositoryHandler.mavenDeployer());
    }

    @Test
    public void mavenDeployerWithName() {
        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
        String expectedName = "someName"
        prepareName(expectedResolver, expectedName)
        assertSame(expectedResolver, repositoryHandler.mavenDeployer(name: expectedName));
    }

//    @Test
//    public void mavenDeployerWithNameAndClosure() {
//        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
//        String expectedName = RepositoryHandler.DEFAULT_MAVEN_DEPLOYER_NAME + "-" +
//                System.identityHashCode(expectedResolver)
//        prepareName(expectedResolver, expectedName)
//        RemoteRepository repositoryDummy = new RemoteRepository()
//        context.checking {
//            one(expectedResolver).setRepository(repositoryDummy)
//        }
//        assertSame(expectedResolver, repositoryHandler.mavenDeployer() {
//            setRepository(repositoryDummy)
//        });
//    }
//
//    @Test
//    public void mavenDeployerWithoutArgsAndWithClosure() {
//        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
//        String expectedName = "someName"
//        prepareName(expectedResolver, expectedName)
//        RemoteRepository repositoryDummy = new RemoteRepository()
//        context.checking {
//            one(expectedResolver).setRepository(repositoryDummy)
//        }
//        assertSame(expectedResolver, repositoryHandler.mavenDeployer(name: expectedName) {
//            setRepository(repositoryDummy)
//        });
//    }

    @Test
    public void mavenInstallerWithoutName() {
        MavenResolver expectedResolver = prepareMavenInstallerTests()
        String expectedName = RepositoryHandler.DEFAULT_MAVEN_INSTALLER_NAME + "-" +
                System.identityHashCode(expectedResolver)
        prepareName(expectedResolver, expectedName)
        assertSame(expectedResolver, repositoryHandler.mavenInstaller());
    }

    @Test
    public void mavenInstallerWithName() {
        MavenResolver expectedResolver = prepareMavenInstallerTests()
        String expectedName = "someName"
        prepareName(expectedResolver, expectedName)
        assertSame(expectedResolver, repositoryHandler.mavenInstaller(name: expectedName));
    }

    @Test
    public void mavenInstallerWithNameAndClosure() {
        MavenResolver expectedResolver = prepareMavenInstallerTests()
        String expectedName = RepositoryHandler.DEFAULT_MAVEN_INSTALLER_NAME + "-" +
                System.identityHashCode(expectedResolver)
        prepareName(expectedResolver, expectedName)
        ResolverSettings resolverSettings = [:] as ResolverSettings
        context.checking {
            one(expectedResolver).setSettings(resolverSettings)
        }
        assertSame(expectedResolver, repositoryHandler.mavenInstaller() {
            setSettings(resolverSettings)
        });
    }

    @Test
    public void mavenInstallerWithoutArgsAndWithClosure() {
        MavenResolver expectedResolver = prepareMavenInstallerTests()
        String expectedName = "someName"
        prepareName(expectedResolver, expectedName)
        ResolverSettings resolverSettings = [:] as ResolverSettings
        context.checking {
            one(expectedResolver).setSettings(resolverSettings)
        }
        assertSame(expectedResolver, repositoryHandler.mavenInstaller(name: expectedName) {
            setSettings(resolverSettings)
        });
    }

    @Test
    public void createIvyRepositoryUsingClosure() {
        IvyArtifactRepository repository = context.mock(TestIvyArtifactRepository.class)
        DependencyResolver resolver = resolver()

        context.checking {
            one(resolverFactoryMock).createIvyRepository()
            will(returnValue(repository))
            one(repository).createResolvers(withParam(Matchers.notNullValue()))
            will { arg -> arg << resolver }
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def arg
        def result = repositoryHandler.ivy {
            arg = it
        }

        assert arg == repository
        assert result == repository
        assert repositoryHandler.resolvers.contains(resolver)
    }

    @Test
    public void createIvyRepositoryUsingAction() {
        IvyArtifactRepository repository = context.mock(TestIvyArtifactRepository.class)
        Action<IvyArtifactRepository> action = context.mock(Action.class)
        DependencyResolver resolver = resolver()

        context.checking {
            one(resolverFactoryMock).createIvyRepository()
            will(returnValue(repository))
            one(action).execute(repository)
            one(repository).createResolvers(withParam(Matchers.notNullValue()))
            will { arg -> arg << resolver }
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def result = repositoryHandler.ivy(action)
        assert result == repository
        assert repositoryHandler.resolvers.contains(resolver)
    }

    @Test
    public void providesADefaultNameForIvyRepository() {
        IvyArtifactRepository repository1 = context.mock(TestIvyArtifactRepository.class)

        context.checking {
            one(resolverFactoryMock).createIvyRepository()
            will(returnValue(repository1))
            one(repository1).getName()
            will(returnValue(null))
            one(repository1).setName("ivy")
            ignoring(repository1)
            allowing(repository1).getName()
            will(returnValue("ivy"))
        }

        repositoryHandler.ivy { }

        IvyArtifactRepository repository2 = context.mock(TestIvyArtifactRepository.class)

        context.checking {
            one(resolverFactoryMock).createIvyRepository()
            will(returnValue(repository2))
            allowing(repository2).getName()
            will(returnValue("ivy2"))
            ignoring(repository2)
        }

        repositoryHandler.ivy { }

        IvyArtifactRepository repository3 = context.mock(TestIvyArtifactRepository.class)

        context.checking {
            one(resolverFactoryMock).createIvyRepository()
            will(returnValue(repository3))
            one(repository3).getName()
            will(returnValue(null))
            one(repository3).setName("ivy3")
            ignoring(repository3)
            allowing(repository3).getName()
            will(returnValue("ivy3"))
        }

        repositoryHandler.ivy { }
    }

    @Test
    public void createMavenRepositoryUsingClosure() {
        MavenArtifactRepository repository = context.mock(TestMavenArtifactRepository.class)
        DependencyResolver resolver = resolver()

        context.checking {
            one(resolverFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(repository).createResolvers(withParam(Matchers.notNullValue()))
            will { arg -> arg << resolver }
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def arg
        def result = repositoryHandler.maven {
            arg = it
        }

        assert arg == repository
        assert result == repository
        assert repositoryHandler.resolvers.contains(resolver)
    }

    @Test
    public void createMavenRepositoryUsingAction() {
        MavenArtifactRepository repository = context.mock(TestMavenArtifactRepository.class)
        Action<MavenArtifactRepository> action = context.mock(Action.class)
        DependencyResolver resolver = resolver()

        context.checking {
            one(resolverFactoryMock).createMavenRepository()
            will(returnValue(repository))
            one(action).execute(repository)
            one(repository).createResolvers(withParam(Matchers.notNullValue()))
            will { arg -> arg << resolver }
            allowing(repository).getName()
            will(returnValue("name"))
        }

        def result = repositoryHandler.maven(action)
        assert result == repository
        assert repositoryHandler.resolvers.contains(resolver)
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

interface TestIvyArtifactRepository extends IvyArtifactRepository, ArtifactRepositoryInternal {
}

interface TestMavenArtifactRepository extends MavenArtifactRepository, ArtifactRepositoryInternal {
}

interface TestFlatDirectoryArtifactRepository extends FlatDirectoryArtifactRepository, ArtifactRepositoryInternal {
}
