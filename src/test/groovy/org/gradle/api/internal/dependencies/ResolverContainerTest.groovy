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

import groovy.mock.interceptor.MockFor
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.gradle.api.InvalidUserDataException

/**
 * @author Hans Dockter
 */
class ResolverContainerTest extends GroovyTestCase {
    ResolverContainer resolverContainer

    def expectedUserDescription
    def expectedUserDescription2
    def expectedUserDescription3

    String expectedName
    String expectedName2
    String expectedName3

    FileSystemResolver expectedResolver
    FileSystemResolver expectedResolver2
    FileSystemResolver expectedResolver3

    MockFor resolverFactoryMocker


    void setUp() {
        resolverContainer = new ResolverContainer()
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
        resolverFactoryMocker = new MockFor(ResolverFactory)
        resolverFactoryMocker.demand.createResolver(0..1) {userDescription ->
            assertEquals(expectedUserDescription, userDescription)
            expectedResolver
        }
        resolverFactoryMocker.demand.createResolver(0..1) {userDescription ->
            assertEquals(expectedUserDescription2, userDescription)
            expectedResolver2
        }
        resolverFactoryMocker.demand.createResolver(0..1) {userDescription ->
            assertEquals(expectedUserDescription3, userDescription)
            expectedResolver3
        }
    }

    void testAddResolver() {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            assert resolverContainer.add(expectedUserDescription).is(expectedResolver)
            assert resolverContainer[expectedName].is(expectedResolver)
            resolverContainer.add(expectedUserDescription2)
        }
        assertEquals([expectedResolver, expectedResolver2], resolverContainer.resolverList)
    }

    void testAddResolverWithClosure() {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            String expectedConfigureValue = 'testvalue'
            Closure configureClosure = {transactional = expectedConfigureValue}
            assert resolverContainer.add(expectedUserDescription, configureClosure).is(expectedResolver)
            assert resolverContainer[expectedName].is(expectedResolver)
            assert expectedResolver.transactional == expectedConfigureValue
        }
    }

    void testAddBefore() {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            resolverContainer.add(expectedUserDescription)
            assert resolverContainer.addBefore(expectedUserDescription2, expectedName).is(expectedResolver2)
        }
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolverList)
    }

    void testAddAfter() {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            resolverContainer.add(expectedUserDescription)
            assert resolverContainer.addAfter(expectedUserDescription2, expectedName).is(expectedResolver2)
            resolverContainer.addAfter(expectedUserDescription3, expectedName)
        }
        assertEquals([expectedResolver, expectedResolver3, expectedResolver2], resolverContainer.resolverList)
    }

    void testAddWithIllegalArgs() {
        checkIllegalArgs('add')
    }

    void testAddFirstWithIllegalArgs() {
        checkIllegalArgs('addFirst')
    }

    void testAddBeforeWithIllegalArgs() {
        checkIllegalArgsForBeforeAndAfter('addBefore')
    }

    void testAddAfterWithIllegalArgs() {
        checkIllegalArgsForBeforeAndAfter('addAfter')
    }

    private void checkIllegalArgs(String methodName) {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            shouldFail(InvalidUserDataException) {
                resolverContainer."$methodName"(null)
            }
        }
    }

    private void checkIllegalArgsForBeforeAndAfter(String methodName) {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            resolverContainer.add(expectedUserDescription)
            shouldFail(InvalidUserDataException) {
                resolverContainer."$methodName"(null, expectedName)
            }
            shouldFail(InvalidUserDataException) {
                resolverContainer."$methodName"(expectedUserDescription2, 'unknownName')
            }
        }

    }

    void testAddFirst() {
        resolverFactoryMocker.use(resolverContainer.resolverFactory) {
            assert resolverContainer.addFirst(expectedUserDescription).is(expectedResolver)
            resolverContainer.addFirst(expectedUserDescription2)
        }
        assertEquals([expectedResolver2, expectedResolver], resolverContainer.resolverList)
    }

}
