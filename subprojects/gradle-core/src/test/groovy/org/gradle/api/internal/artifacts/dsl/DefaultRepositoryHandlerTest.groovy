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

import org.apache.ivy.plugins.resolver.ResolverSettings
import org.apache.maven.artifact.ant.RemoteRepository
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.artifacts.DefaultResolverContainerTest
import org.gradle.util.HashUtil
import org.junit.Test
import static org.junit.Assert.*
import org.gradle.api.internal.AsmBackedClassGenerator

/**
 * @author Hans Dockter
 */
class DefaultRepositoryHandlerTest extends DefaultResolverContainerTest {
    static final String TEST_REPO_URL = 'http://www.gradle.org'

    private DefaultRepositoryHandler repositoryHandler

    public ResolverContainer createResolverContainer() {
        AsmBackedClassGenerator classGenerator = new AsmBackedClassGenerator()
        repositoryHandler = classGenerator.newInstance(DefaultRepositoryHandler.class, resolverFactoryMock, classGenerator);
        return repositoryHandler;
    }

    @Test public void testFlatDirWithNameAndDirs() {
        String resolverName = 'libs'
        prepareFlatDirResolverCreation(resolverName, createFlatDirTestDirs())
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.flatDir([name: resolverName] + [dirs: createFlatDirTestDirsArgs()]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }

    @Test public void testFlatDirWithNameAndSingleDir() {
        String resolverName = 'libs'
        prepareFlatDirResolverCreation(resolverName, ['a' as File] as File[])
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.flatDir([name: resolverName] + [dirs: 'a']).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }

    @Test public void testFlatDirWithoutNameAndWithDirs() {
        Object[] expectedDirs = createFlatDirTestDirs()
        String expectedName = HashUtil.createHash(expectedDirs.join(''))
        prepareFlatDirResolverCreation(expectedName, expectedDirs)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.flatDir([dirs: createFlatDirTestDirsArgs()]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.getResolvers())
    }

    @Test (expected = InvalidUserDataException)
    public void testFlatDirWithMissingDirs() {
        repositoryHandler.flatDir([name: 'someName'])
    }

    @Test
    public void testMavenCentralWithNoArgs() {
        prepareCreateMavenRepo(ResolverContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME, ResolverContainer.MAVEN_CENTRAL_URL)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenCentral().is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenCentralWithSingleUrl() {
        String testUrl2 = 'http://www.gradle2.org'
        prepareCreateMavenRepo(ResolverContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME, ResolverContainer.MAVEN_CENTRAL_URL, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenCentral(urls: testUrl2).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenCentralWithNameAndUrls() {
        String testUrl1 = 'http://www.gradle1.org'
        String testUrl2 = 'http://www.gradle2.org'
        String name = 'customName'
        prepareCreateMavenRepo(name, ResolverContainer.MAVEN_CENTRAL_URL, testUrl1, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenCentral(name: name, urls: [testUrl1, testUrl2]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenLocalWithNoArgs() {
        context.checking {
            one(resolverFactoryMock).createMavenLocalResolver(ResolverContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME)
            will(returnValue(expectedResolver))
        }
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenLocal().is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test(expected = InvalidUserDataException)
    public void testMavenRepoWithMissingUrls() {
        repositoryHandler.mavenRepo([name: 'someName'])
    }

    @Test
    public void testMavenRepoWithNameAndUrls() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'
        prepareCreateMavenRepo(repoName, repoRoot, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenRepo([name: repoName, urls: [repoRoot, testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithNameAndRootUrlOnly() {
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'
        prepareCreateMavenRepo(repoName, repoRoot)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenRepo([name: repoName, urls: repoRoot]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    @Test
    public void testMavenRepoWithoutName() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        prepareCreateMavenRepo(repoRoot, repoRoot, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert repositoryHandler.mavenRepo([urls: [repoRoot, testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], repositoryHandler.resolvers)
    }

    private prepareCreateMavenRepo(String name, String mavenUrl, String[] jarUrls) {
        context.checking {
            one(resolverFactoryMock).createMavenRepoResolver(name, mavenUrl, jarUrls);
            will(returnValue(expectedResolver))
        }
    }

    private def prepareFlatDirResolverCreation(String expectedName, File[] expectedDirs) {
        context.checking {
          one(resolverFactoryMock).createFlatDirResolver(expectedName, expectedDirs); will(returnValue(expectedResolver))
        }
    }

    private List createFlatDirTestDirsArgs() {
        return ['a', 'b' as File]
    }

    private File[] createFlatDirTestDirs() {
        return ['a' as File, 'b' as File] as File[]
    }

    private def prepareResolverFactoryToTakeAndReturnExpectedResolver() {
      context.checking {
        one(resolverFactoryMock).createResolver(expectedResolver); will(returnValue(expectedResolver))
      }
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

    @Test
    public void mavenDeployerWithNameAndClosure() {
        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
        String expectedName = RepositoryHandler.DEFAULT_MAVEN_DEPLOYER_NAME + "-" +
                System.identityHashCode(expectedResolver)
        prepareName(expectedResolver, expectedName) 
        RemoteRepository repositoryDummy = new RemoteRepository()
        context.checking {
            one(expectedResolver).setRepository(repositoryDummy)
        }
        assertSame(expectedResolver, repositoryHandler.mavenDeployer() {
            setRepository(repositoryDummy)
        });
    }

    @Test
    public void mavenDeployerWithoutArgsAndWithClosure() {
        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
        String expectedName = "someName"
        prepareName(expectedResolver, expectedName) 
        RemoteRepository repositoryDummy = new RemoteRepository()
        context.checking {
            one(expectedResolver).setRepository(repositoryDummy)
        }
        assertSame(expectedResolver, repositoryHandler.mavenDeployer(name: expectedName) {
            setRepository(repositoryDummy)
        });
    }

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

    private void prepareName(mavenResolver, String expectedName) {
        context.checking {
            one(mavenResolver).setName(expectedName)
        }
    }
}
