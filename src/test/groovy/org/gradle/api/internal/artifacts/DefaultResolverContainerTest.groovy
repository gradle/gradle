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

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.core.cache.RepositoryCacheManager
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.ConventionTestHelper
import org.gradle.api.internal.artifacts.ConfigurationContainer
import org.gradle.api.internal.artifacts.DefaultResolverContainer
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory
import org.gradle.api.internal.plugins.DefaultConvention
import org.gradle.util.HashUtil
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertSame

/**
 * @author Hans Dockter
 */
class DefaultResolverContainerTest {

  static final String TEST_REPO_NAME = 'reponame'
    static final String TEST_REPO_URL = 'http://www.gradle.org'
    DefaultResolverContainer resolverContainer

    RepositoryCacheManager dummyCacheManager = new DefaultRepositoryCacheManager()
    def expectedUserDescription
    def expectedUserDescription2
    def expectedUserDescription3
    String expectedName
    String expectedName2
    String expectedName3

    FileSystemResolver expectedResolver
    FileSystemResolver expectedResolver2
    FileSystemResolver expectedResolver3

    ResolverFactory resolverFactoryMock;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        expectedUserDescription = 'somedescription'
        expectedUserDescription2 = 'somedescription2'
        expectedUserDescription3 = 'somedescription3'
        expectedName = 'somename'
        expectedName2 = 'somename2'
        expectedName3 = 'somename3'
        expectedResolver = new FileSystemResolver()
        expectedResolver2 = new FileSystemResolver()
        expectedResolver3 = new FileSystemResolver()
        expectedResolver.name = expectedName
        expectedResolver2.name = expectedName2
        expectedResolver3.name = expectedName3
        resolverFactoryMock = context.mock(ResolverFactory)
        context.checking {
            allowing(resolverFactoryMock).createResolver(expectedUserDescription); will(returnValue(expectedResolver))
            allowing(resolverFactoryMock).createResolver(expectedUserDescription2); will(returnValue(expectedResolver2))
            allowing(resolverFactoryMock).createResolver(expectedUserDescription3); will(returnValue(expectedResolver3))
        }
        resolverContainer = new DefaultResolverContainer(resolverFactoryMock, new DefaultConvention())
    }

    @Test public void testAddResolver() {
        assert resolverContainer.add(expectedUserDescription).is(expectedResolver)
        assert resolverContainer.resolver(expectedName).is(expectedResolver)
        resolverContainer.add(expectedUserDescription2)
        assertEquals([expectedResolver, expectedResolver2], resolverContainer.resolverList)
    }

    @Test public void testAddResolverWithClosure() {
        String expectedConfigureValue = 'testvalue'
        Closure configureClosure = {transactional = expectedConfigureValue}
        assert resolverContainer.add(expectedUserDescription, configureClosure).is(expectedResolver)
        assert resolverContainer.resolver(expectedName).is(expectedResolver)
        assert expectedResolver.transactional == expectedConfigureValue
    }

    @Test public void testAddBefore() {
        resolverContainer.add(expectedUserDescription)
        assert resolverContainer.addBefore(expectedUserDescription2, expectedName).is(expectedResolver2)
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolverList)
    }

    @Test public void testAddAfter() {
        resolverContainer.add(expectedUserDescription)
        assert resolverContainer.addAfter(expectedUserDescription2, expectedName).is(expectedResolver2)
        resolverContainer.addAfter(expectedUserDescription3, expectedName)
        assertEquals([expectedResolver, expectedResolver3, expectedResolver2], resolverContainer.resolverList)
    }

    @Test (expected = InvalidUserDataException) public void testAddWithNullUserDescription() {
        resolverContainer.add(null)
    }

    @Test (expected = InvalidUserDataException) public void testAddFirstWithNullUserDescription() {
        resolverContainer.addFirst(null)
    }

    @Test (expected = InvalidUserDataException) public void testAddBeforeWithNullUserDescription() {
        resolverContainer.addBefore(null, expectedName)
    }

    @Test (expected = InvalidUserDataException) public void testAddBeforeWithUnknownResolver() {
        resolverContainer.addBefore(expectedUserDescription2, 'unknownName')
    }

    @Test (expected = InvalidUserDataException) public void testAddAfterWithNullUserDescription() {
        resolverContainer.addAfter(null, expectedName)
    }

    @Test (expected = InvalidUserDataException) public void testAddAfterWithUnknownResolver() {
        resolverContainer.addBefore(expectedUserDescription2, 'unknownName')
    }

    @Test public void testAddFirst() {
        assert resolverContainer.addFirst(expectedUserDescription).is(expectedResolver)
        resolverContainer.addFirst(expectedUserDescription2)
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolverList)
    }

    @Test public void testCreateFlatDirResolver() {
        prepareFlatDirResolverCreation('libs', createFlatDirTestDirs())
        assert resolverContainer.createFlatDirResolver("libs", createFlatDirTestDirsArgs() as Object[]).is(expectedResolver)
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

    @Test public void testFlatDirWithNameAndRootDirs() {
        String resolverName = 'libs'
        prepareFlatDirResolverCreation(resolverName, createFlatDirTestDirs())
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.flatDir([name: resolverName] + [rootDirs: createFlatDirTestDirsArgs()]).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test public void testFlatDirWithNameAndRootDir() {
        String resolverName = 'libs'
        prepareFlatDirResolverCreation(resolverName, ['a' as File] as File[])
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.flatDir([name: resolverName] + [rootDir: 'a']).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test public void testFlatDirWithoutNameAndRootDirs() {
        Object[] expectedDirs = createFlatDirTestDirs()
        String expectedName = HashUtil.createHash(expectedDirs.join(''))
        prepareFlatDirResolverCreation(expectedName, expectedDirs)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.flatDir([rootDirs: createFlatDirTestDirsArgs()]).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test(expected = InvalidUserDataException)
    public void testFlatDirWithRootDirAndRootDirsSet() {
        resolverContainer.flatDir([rootDir: 'a', rootDirs: ['b', 'c']])
    }

    @Test(expected = InvalidUserDataException)
    public void testFlatDirWithMissingRootDirAndMissingRootDirs() {
        resolverContainer.flatDir([name: 'someName'])
    }

    @Test
    public void testCreateMavenRepo() {
        String testUrl2 = 'http://www.gradle2.org'
        prepareCreateMavenRepo(TEST_REPO_NAME, TEST_REPO_URL, testUrl2)
        assert resolverContainer.createMavenRepoResolver(TEST_REPO_NAME, TEST_REPO_URL, [testUrl2] as String[]).is(expectedResolver)
    }

    @Test
    public void testMavenCentralWithNoArgs() {
        prepareCreateMavenRepo(ResolverContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME, ResolverContainer.MAVEN_CENTRAL_URL)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.mavenCentral().is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test
    public void testMavenCentral() {
        String testUrl2 = 'http://www.gradle2.org'
        prepareCreateMavenRepo(ResolverContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME, ResolverContainer.MAVEN_CENTRAL_URL, testUrl2) 
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.mavenCentral(jarOnlyRepos: [testUrl2]).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test
    public void testMavenCentralWithName() {
        String testUrl2 = 'http://www.gradle2.org'
        String name = 'customName'
        prepareCreateMavenRepo(name, ResolverContainer.MAVEN_CENTRAL_URL, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.mavenCentral(name: name, jarOnlyRepos: [testUrl2]).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test(expected = InvalidUserDataException)
    public void testMavenRepoWithMissingRootRepo() {
        resolverContainer.mavenRepo([name: 'someName'])
    }

    @Test
    public void testMavenRepoWithName() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        String repoName = 'mavenRepoName'
        prepareCreateMavenRepo(repoName, repoRoot, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.mavenRepo([name: repoName, rootRepo: repoRoot, jarOnlyRepos: [testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    @Test
    public void testMavenRepoWithoutName() {
        String testUrl2 = 'http://www.gradle2.org'
        String repoRoot = 'http://www.reporoot.org'
        prepareCreateMavenRepo(repoRoot, repoRoot, testUrl2)
        prepareResolverFactoryToTakeAndReturnExpectedResolver()
        assert resolverContainer.mavenRepo([rootRepo: repoRoot, jarOnlyRepos: [testUrl2]]).is(expectedResolver)
        assertEquals([expectedResolver], resolverContainer.resolverList)
    }

    private prepareCreateMavenRepo(String name, String mavenUrl, String[] jarUrls) {
        context.checking {
            one(resolverFactoryMock).createMavenRepoResolver(name, mavenUrl, jarUrls);
            will(returnValue(expectedResolver))
        }
    }

    @Test
    public void createMavenUploader() {
        assertSame(prepareMavenDeployerTests(), resolverContainer.createMavenDeployer(TEST_REPO_NAME));
    }

    @Test
    public void addMavenUploader() {
        GroovyMavenDeployer expectedResolver = prepareMavenDeployerTests()
        context.checking {
            one(resolverFactoryMock).createResolver(expectedResolver);
            will(returnValue(expectedResolver))
        }
        assertSame(expectedResolver, resolverContainer.addMavenDeployer(TEST_REPO_NAME));
        assert resolverContainer.resolver(TEST_REPO_NAME).is(expectedResolver)
    }

    @Test
    public void createMavenInstaller() {
        assertSame(prepareMavenInstallerTests(), resolverContainer.createMavenInstaller(TEST_REPO_NAME));
    }

    @Test
    public void addMavenInstaller() {
        DependencyResolver expectedResolver = prepareMavenInstallerTests()
        context.checking {
            one(resolverFactoryMock).createResolver(expectedResolver);
            will(returnValue(expectedResolver))
        }
        assertSame(expectedResolver, resolverContainer.addMavenInstaller(TEST_REPO_NAME));
        assert resolverContainer.resolver(TEST_REPO_NAME).is(expectedResolver)
    }

    private GroovyMavenDeployer prepareMavenDeployerTests() {
        prepareMavenResolverTests(GroovyMavenDeployer, "createMavenDeployer")
    }

    private DependencyResolver prepareMavenInstallerTests() {
        prepareMavenResolverTests(MavenResolver, "createMavenInstaller")
    }

    private DependencyResolver prepareMavenResolverTests(Class resolverType, String createMethod) {
        File testPomDir = new File("pomdir");
        ConfigurationContainer configurationContainer = [:] as ConfigurationContainer
        Conf2ScopeMappingContainer conf2ScopeMappingContainer = [:] as Conf2ScopeMappingContainer
        resolverContainer.setMavenPomDir(testPomDir)
        resolverContainer.setConfigurationContainer(configurationContainer)
        resolverContainer.setMavenScopeMappings(conf2ScopeMappingContainer)
        DependencyResolver expectedResolver = context.mock(resolverType)
        context.checking {
            allowing(expectedResolver).getName(); will(returnValue(TEST_REPO_NAME))
            one(resolverFactoryMock)."$createMethod"(TEST_REPO_NAME, testPomDir, configurationContainer, conf2ScopeMappingContainer);
            will(returnValue(expectedResolver))
        }
        expectedResolver
    }

  @Test
    public void testConventionAwareness() {
        ConventionTestHelper conventionTestHelper = new ConventionTestHelper()
        resolverContainer.setConventionAwareHelper(conventionTestHelper.conventionAwareHelperMock)
    }
}
