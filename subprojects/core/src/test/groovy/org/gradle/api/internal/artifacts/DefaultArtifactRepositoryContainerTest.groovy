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
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.UnknownRepositoryException
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.internal.reflect.Instantiator
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
@RunWith(JMock)
class DefaultArtifactRepositoryContainerTest {
    static final String TEST_REPO_NAME = 'reponame'

    DefaultArtifactRepositoryContainer resolverContainer

    def expectedUserDescription
    def expectedUserDescription2
    def expectedUserDescription3
    String expectedName
    String expectedName2
    String expectedName3

    FileSystemResolver expectedResolver
    FileSystemResolver expectedResolver2
    FileSystemResolver expectedResolver3

    BaseRepositoryFactory baseRepositoryFactoryMock;

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    ArtifactRepositoryContainer createResolverContainer() {
        return new DefaultArtifactRepositoryContainer(baseRepositoryFactoryMock, context.mock(Instantiator.class))
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
        baseRepositoryFactoryMock = context.mock(BaseRepositoryFactory)
        ArtifactRepository repo1 = context.mock(TestArtifactRepository)
        ArtifactRepository repo2 = context.mock(TestArtifactRepository)
        ArtifactRepository repo3 = context.mock(TestArtifactRepository)
        context.checking {
            allowing(baseRepositoryFactoryMock).createRepository(expectedUserDescription); will(returnValue(repo1))
            allowing(baseRepositoryFactoryMock).createRepository(expectedUserDescription2); will(returnValue(repo2))
            allowing(baseRepositoryFactoryMock).createRepository(expectedUserDescription3); will(returnValue(repo3))
            allowing(repo1).createResolver(); will(returnValue(expectedResolver))
            allowing(repo2).createResolver(); will(returnValue(expectedResolver2))
            allowing(repo3).createResolver(); will(returnValue(expectedResolver3))
        }
        resolverContainer = createResolverContainer()
    }

    @Test public void testAddResolver() {
        assert resolverContainer.addLast(expectedUserDescription).is(expectedResolver)
        assert resolverContainer.findByName(expectedName) != null
        resolverContainer.addLast(expectedUserDescription2)
        assertEquals([expectedResolver, expectedResolver2], resolverContainer.resolvers)
    }

    @Test public void testCannotAddResolverWithDuplicateName() {
        [expectedResolver, expectedResolver2]*.name = 'resolver'
        resolverContainer.addLast(expectedUserDescription)

        try {
            resolverContainer.addLast(expectedUserDescription2)
            fail()
        } catch (InvalidUserDataException e) {
            assertThat(e.message, equalTo("Cannot add a repository with name 'resolver' as a repository with that name already exists."))
        }
    }

    @Test public void testAddResolverWithClosure() {
        def expectedConfigureValue = 'testvalue'
        Closure configureClosure = {transactional = expectedConfigureValue}
        assertThat(resolverContainer.addLast(expectedUserDescription, configureClosure), sameInstance(expectedResolver))
        assertThat(resolverContainer.findByName(expectedName), notNullValue())
        assert expectedResolver.transactional == expectedConfigureValue
    }

    @Test public void testAddBefore() {
        resolverContainer.addLast(expectedUserDescription)
        assert resolverContainer.addBefore(expectedUserDescription2, expectedName).is(expectedResolver2)
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolvers)
    }

    @Test public void testAddAfter() {
        resolverContainer.addLast(expectedUserDescription)
        assert resolverContainer.addAfter(expectedUserDescription2, expectedName).is(expectedResolver2)
        resolverContainer.addAfter(expectedUserDescription3, expectedName)
        assertEquals([expectedResolver, expectedResolver3, expectedResolver2], resolverContainer.resolvers)
    }

    @Test(expected = UnknownRepositoryException) public void testAddBeforeWithUnknownResolver() {
        resolverContainer.addBefore(expectedUserDescription2, 'unknownName')
    }

    @Test(expected = UnknownRepositoryException) public void testAddAfterWithUnknownResolver() {
        resolverContainer.addBefore(expectedUserDescription2, 'unknownName')
    }

    @Test public void testAddFirst() {
        ArtifactRepository repository1 = context.mock(ArtifactRepository)
        ArtifactRepository repository2 = context.mock(ArtifactRepository)

        context.checking {
            allowing(repository1).getName(); will(returnValue("1"))
            allowing(repository2).getName(); will(returnValue("2"))
        }

        resolverContainer.addFirst(repository1)
        resolverContainer.addFirst(repository2)

        assert resolverContainer == [repository2, repository1]
        assert resolverContainer.collect { it } == [repository2, repository1]
        assert resolverContainer.matching { true } == [repository2, repository1]
        assert resolverContainer.matching { true }.collect { it } == [repository2, repository1]
    }

    @Test public void testAddLast() {
        ArtifactRepository repository1 = context.mock(ArtifactRepository)
        ArtifactRepository repository2 = context.mock(ArtifactRepository)

        context.checking {
            allowing(repository1).getName(); will(returnValue('repo1'))
            allowing(repository2).getName(); will(returnValue('repo2'))
        }
        
        resolverContainer.addLast(repository1)
        resolverContainer.addLast(repository2)

        assert resolverContainer == [repository1, repository2]
    }
    
    @Test public void testAddFirstUsingUserDescription() {
        assert resolverContainer.addFirst(expectedUserDescription).is(expectedResolver)
        resolverContainer.addFirst(expectedUserDescription2)
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolvers)
    }

    @Test public void testAddLastUsingUserDescription() {
        assert resolverContainer.addLast(expectedUserDescription).is(expectedResolver)
        resolverContainer.addLast(expectedUserDescription2)
        assertEquals([expectedResolver, expectedResolver2], resolverContainer.resolvers)
    }

    @Test
    public void testAddWithUnnamedResolver() {
        expectedResolver.name = null
        assert resolverContainer.addLast(expectedUserDescription).is(expectedResolver)
        assert expectedResolver.name == 'repository'
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
    public void notificationsAreFiredWhenRepositoryIsAdded() {
        Action<DependencyResolver> action = context.mock(Action.class)
        ArtifactRepository repository = context.mock(ArtifactRepository)

        context.checking {
            ignoring(repository)
            one(action).execute(repository)
        }

        resolverContainer.all(action)
        resolverContainer.add(repository)
    }

    @Test
    public void notificationsAreFiredWhenRepositoryIsAddedToTheHead() {
        Action<DependencyResolver> action = context.mock(Action.class)
        ArtifactRepository repository = context.mock(ArtifactRepository)

        context.checking {
            ignoring(repository)
            one(action).execute(repository)
        }

        resolverContainer.all(action)
        resolverContainer.addFirst(repository)
    }

    @Test
    public void notificationsAreFiredWhenRepositoryIsAddedToTheTail() {
        Action<DependencyResolver> action = context.mock(Action.class)
        ArtifactRepository repository = context.mock(ArtifactRepository)

        context.checking {
            ignoring(repository)
            one(action).execute(repository)
        }

        resolverContainer.all(action)
        resolverContainer.addLast(repository)
    }
}

interface TestArtifactRepository extends ArtifactRepository, ArtifactRepositoryInternal {
}
