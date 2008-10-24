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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.core.cache.RepositoryCacheManager
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.maven.GroovyMavenUploader
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultConf2ScopeMappingContainer
import org.gradle.util.JUnit4GroovyMockery
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.gradle.api.dependencies.maven.GroovyMavenUploader

/**
 * @author Hans Dockter
 */
class ResolverContainerTest {
    static final String TEST_REPO_NAME = 'reponame'
    static final String TEST_REPO_URL = 'http://www.gradle.org'
    ResolverContainer resolverContainer

    LocalReposCacheHandler localReposCacheHandler

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
        resolverContainer = new ResolverContainer(resolverFactoryMock)
    }

    @Test public void testInit() {
        assert resolverContainer.localReposCacheHandler.is(localReposCacheHandler)
    }

    @Test public void testAddResolver() {
        assert resolverContainer.add(expectedUserDescription).is(expectedResolver)
        assert resolverContainer[expectedName].is(expectedResolver)
        resolverContainer.add(expectedUserDescription2)
        assertEquals([expectedResolver, expectedResolver2], resolverContainer.resolverList)
    }

    @Test public void testAddResolverWithClosure() {
        String expectedConfigureValue = 'testvalue'
        Closure configureClosure = {transactional = expectedConfigureValue}
        assert resolverContainer.add(expectedUserDescription, configureClosure).is(expectedResolver)
        assert resolverContainer[expectedName].is(expectedResolver)
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
        File[] expectedRoots = [new File('/rootFolder')]
        String expectedName = 'libs'
        context.checking {
            one(resolverFactoryMock).createFlatDirResolver(expectedName, expectedRoots); will(returnValue(expectedResolver))
        }
        assert resolverContainer.createFlatDirResolver(expectedName, expectedRoots as File[]).is(expectedResolver)
    }

    @Test
    public void testCreateMavenRepo() {
        String testUrl2 = 'http://www.gradle2.org'
        context.checking {
            one(resolverFactoryMock).createMavenRepoResolver(TEST_REPO_NAME, TEST_REPO_URL, [testUrl2] as String[]);
            will(returnValue(expectedResolver))
        }
        assert resolverContainer.createMavenRepoResolver(TEST_REPO_NAME, TEST_REPO_URL, [testUrl2] as String[]).is(expectedResolver)
    }

    @Test
    public void createMavenUploader() {
        assertSame(prepareMavenUploaderTests(), resolverContainer.createMavenUploader(TEST_REPO_NAME));
    }

    @Test
    public void addMavenUploader() {
        GroovyMavenUploader expectedResolver = prepareMavenUploaderTests()
        context.checking {
            one(resolverFactoryMock).createResolver(expectedResolver);
            will(returnValue(expectedResolver))
        }
        assertSame(expectedResolver, resolverContainer.addMavenUploader(TEST_REPO_NAME));
        assert resolverContainer[TEST_REPO_NAME].is(expectedResolver)
    }

    private GroovyMavenUploader prepareMavenUploaderTests() {
        File testPomDir = new File("pomdir");
        Conf2ScopeMappingContainer conf2ScopeMappingContainer = new DefaultConf2ScopeMappingContainer();
        DependencyManager dependencyManager = [:] as DependencyManager
        resolverContainer.setMavenPomDir(testPomDir)
        resolverContainer.setMavenConf2ScopeMappings(conf2ScopeMappingContainer)
        resolverContainer.setDependencyManager(dependencyManager)
        GroovyMavenUploader expectedResolver = context.mock(GroovyMavenUploader)
        context.checking {
            allowing(expectedResolver).getName(); will(returnValue(TEST_REPO_NAME))
            one(resolverFactoryMock).createMavenUploader(TEST_REPO_NAME, testPomDir, conf2ScopeMappingContainer,
                dependencyManager);
            will(returnValue(expectedResolver))
        }
        expectedResolver
    }
}
