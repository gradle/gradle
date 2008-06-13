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

package org.gradle.api.dependencies

import groovy.mock.interceptor.MockFor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.internal.dependencies.AbstractDependencyContainerTest
import org.gradle.api.internal.dependencies.DefaultDependencyContainer
import org.gradle.api.internal.dependencies.DependencyFactory
import org.gradle.api.internal.dependencies.DependenciesUtil
import org.gradle.api.internal.project.DefaultProject
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class ClientModuleTest extends AbstractDependencyContainerTest {
    ClientModule clientModule

    String testId = 'org.gradle:test:1.3.4'

    Map testModuleRegistry = [a: 'a']

    MockFor dependencyFactoryMocker

    List parentConfs = ['parentConf']

    MockFor dependencyDescriptorFactoryMock

    DefaultDependencyDescriptor expectedDependencyDescriptor

    public DefaultDependencyContainer getTestObj() {
        clientModule
    }

    void setUp() {
        super.setUp();
        clientModule = new ClientModule(dependencyFactory, parentConfs as Set, testId, testModuleRegistry)
        clientModule.project = project
        dependencyFactoryMocker = new MockFor(DependencyFactory)
        testDefaultConfs = clientModule.defaultConfs
        testConfs = clientModule.defaultConfs
        dependencyDescriptorFactoryMock = new MockFor(DependencyDescriptorFactory)
        clientModule.dependencyDescriptorFactory = [:] as DependencyDescriptorFactory
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor()
    }

    void testInit() {
        assertEquals(clientModule.defaultConfs, [Dependency.DEFAULT_CONFIGURATION])
        assertEquals(clientModule.confs, parentConfs as Set)
        assertEquals(clientModule.id, testId)
        assertEquals(clientModule.clientModuleRegistry, testModuleRegistry)
    }

    void testCreateDependencyDescriptor() {
        String testDependencyUserDescription = "org.apache:test:5.0.4"
        DependencyDescriptor testDependencyDescriptor = [:] as DependencyDescriptor
        Dependency testDependency = [createDepencencyDescriptor: {testDependencyDescriptor}] as Dependency
        dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependency, DefaultProject project ->
            assertEquals(testDependencyUserDescription, userDependency)
            testDependency
        }
        dependencyDescriptorFactoryMock.demand.createDescriptor(1..1) {String descriptor, boolean force, boolean transitive,
                                                                       boolean changing, Set confs, List excludeRules, Map extraAttributes ->
            assertEquals(testId, descriptor)
            assert !force
            assert transitive
            assert changing
            assertEquals(clientModule.confs, confs)
            assertEquals([], excludeRules)
            assertEquals([(ClientModule.CLIENT_MODULE_KEY): testId], extraAttributes)
            expectedDependencyDescriptor
        }
        
        DependencyDescriptor dependencyDescriptor
        dependencyDescriptorFactoryMock.use(clientModule.dependencyDescriptorFactory) {
            dependencyFactoryMocker.use(clientModule.dependencyFactory) {
                clientModule.dependencies(testDependencyUserDescription)
                assert clientModule.createDepencencyDescriptor().is(expectedDependencyDescriptor)
            }
        }
        ModuleDescriptor moduleDescriptor = testModuleRegistry[testId]
        assert moduleDescriptor
        assert moduleDescriptor.moduleRevisionId == expectedDependencyDescriptor.dependencyRevisionId
        assert moduleDescriptor.dependencies[0].is(testDependencyDescriptor)
    }

    public void testUnsupportedOperations() {
        shouldFail(UnsupportedOperationException) {
            clientModule.clientModule(['a'], 'a')
        }
        shouldFail(UnsupportedOperationException) {
            clientModule.dependencies(['a'], 'a')
        }
        shouldFail(UnsupportedOperationException) {
            clientModule.dependency(['a'], 'a')
        }
    }


}
