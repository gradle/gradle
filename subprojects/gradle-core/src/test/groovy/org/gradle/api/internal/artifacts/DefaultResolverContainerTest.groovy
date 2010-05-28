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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.artifacts.UnknownRepositoryException
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.GroovyMavenDeployer
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.ClassGenerator

/**
 * @author Hans Dockter
 */
class DefaultResolverContainerTest {
    static final String TEST_REPO_NAME = 'reponame'
    
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

    ResolverContainer createResolverContainer() {
        return new DefaultResolverContainer(resolverFactoryMock, context.mock(ClassGenerator.class))
    }

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
        resolverContainer = createResolverContainer()
    }

    @Test public void testAddResolver() {
        assert resolverContainer.add(expectedUserDescription).is(expectedResolver)
        assert resolverContainer.findByName(expectedName).is(expectedResolver)
        resolverContainer.add(expectedUserDescription2)
        assertEquals([expectedResolver, expectedResolver2], resolverContainer.resolvers)
    }

    @Test public void testCannotAddResolverWithDuplicateName() {
        [expectedResolver, expectedResolver2]*.name = 'resolver'
        resolverContainer.add(expectedUserDescription)

        try {
            resolverContainer.add(expectedUserDescription2)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("Cannot add a resolver with name 'resolver' as a resolver with that name already exists."))
        }
    }

    @Test public void testAddResolverWithClosure() {
        def expectedConfigureValue = 'testvalue'
        Closure configureClosure = {transactional = expectedConfigureValue}
        assertThat(resolverContainer.add(expectedUserDescription, configureClosure), sameInstance(expectedResolver))
        assertThat(resolverContainer.findByName(expectedName), sameInstance(expectedResolver))
        assert expectedResolver.transactional == expectedConfigureValue
    }

    @Test public void testAddBefore() {
        resolverContainer.add(expectedUserDescription)
        assert resolverContainer.addBefore(expectedUserDescription2, expectedName).is(expectedResolver2)
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolvers)
    }

    @Test public void testAddAfter() {
        resolverContainer.add(expectedUserDescription)
        assert resolverContainer.addAfter(expectedUserDescription2, expectedName).is(expectedResolver2)
        resolverContainer.addAfter(expectedUserDescription3, expectedName)
        assertEquals([expectedResolver, expectedResolver3, expectedResolver2], resolverContainer.resolvers)
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
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolvers)
    }

    @Test(expected =  InvalidUserDataException)
    public void testAddWithUnnamedResolver() {
        expectedResolver.name = null
        resolverContainer.add(expectedUserDescription).is(expectedResolver)
    }

    @Test
    public void testGetThrowsExceptionForUnknownResolver() {
        try {
            resolverContainer.getByName('unknown')
            fail()
        } catch (UnknownRepositoryException e) {
            assertThat(e.message, equalTo("Repository with name 'unknown' not found."))
        }
    }
    
    @Test
    public void createMavenUploader() {
        // todo we have to specify the class name, as this class is extended. This is a Groovy bug. As soon as we switch to a new Groovy version, we can refactor this. 
        assertSame(prepareMavenDeployerTests(), resolverContainer.createMavenDeployer(DefaultResolverContainerTest.TEST_REPO_NAME));
    }

    @Test
    public void createMavenInstaller() {
        assertSame(prepareMavenInstallerTests(), resolverContainer.createMavenInstaller(DefaultResolverContainerTest.TEST_REPO_NAME));
    }

    protected GroovyMavenDeployer prepareMavenDeployerTests() {
        prepareMavenResolverTests(GroovyMavenDeployer, "createMavenDeployer")
    }

    protected DependencyResolver prepareMavenInstallerTests() {
        prepareMavenResolverTests(MavenResolver, "createMavenInstaller")
    }

    protected DependencyResolver prepareMavenResolverTests(Class resolverType, String createMethod) {
        File testPomDir = new File("pomdir");
        ConfigurationContainer configurationContainer = [:] as ConfigurationContainer
        Conf2ScopeMappingContainer conf2ScopeMappingContainer = [:] as Conf2ScopeMappingContainer
        FileResolver fileResolver = [:] as FileResolver
        resolverContainer.setMavenPomDir(testPomDir)
        resolverContainer.setConfigurationContainer(configurationContainer)
        resolverContainer.setMavenScopeMappings(conf2ScopeMappingContainer)
        resolverContainer.setFileResolver(fileResolver)
        DependencyResolver expectedResolver = context.mock(resolverType)
        context.checking {
            allowing(expectedResolver).getName(); will(returnValue(DefaultResolverContainerTest.TEST_REPO_NAME))
            allowing(resolverFactoryMock)."$createMethod"(
                    withParam(any(String)),
                    withParam(same(resolverContainer)),
                    withParam(same(configurationContainer)),
                    withParam(same(conf2ScopeMappingContainer)),
                    withParam(same(fileResolver)));
            will(returnValue(expectedResolver))
            one(resolverFactoryMock).createResolver(expectedResolver);
            will(returnValue(expectedResolver))
        }
        expectedResolver
    }
}
