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

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.internal.dependencies.AbstractDependencyContainerTest
import org.gradle.api.internal.dependencies.DefaultDependencyContainer
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory
import org.gradle.util.HelperUtil
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock)
class ClientModuleTest extends AbstractDependencyContainerTest {
    ClientModule clientModule

    String testId = 'org.gradle:test:1.3.4'

    Map testModuleRegistry = [a: 'a']

    List parentConfs = ['parentConf']

    DependencyDescriptorFactory dependencyDescriptorFactoryMock

    DefaultDependencyDescriptor expectedDependencyDescriptor

    public DefaultDependencyContainer getTestObj() {
        clientModule
    }

    @Before public void setUp() {
        super.setUp();
        context.setImposteriser(ClassImposteriser.INSTANCE)
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory)
        clientModule = new ClientModule(dependencyFactory, parentConfs as Set, testId, testModuleRegistry)
        clientModule.project = project
        testDefaultConfs = clientModule.defaultConfs
        testConfs = clientModule.defaultConfs
        clientModule.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock)
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor()
    }

    @Test public void testInit() {
        assertEquals(clientModule.defaultConfs, [Dependency.DEFAULT_CONFIGURATION])
        assertEquals(clientModule.confs, parentConfs as Set)
        assertEquals(clientModule.id, testId)
        assertEquals(clientModule.clientModuleRegistry, testModuleRegistry)
    }

    @Test public void testCreateDependencyDescriptor() {
        String testDependencyUserDescription = "org.apache:test:5.0.4"
        DependencyDescriptor testDependencyDescriptor = [:] as DependencyDescriptor
        Dependency testDependency = [createDepencencyDescriptor: {testDependencyDescriptor}] as Dependency

        context.checking {
            one(dependencyFactory).createDependency(new HashSet(clientModule.defaultConfs), testDependencyUserDescription, project);
            will(returnValue(testDependency))
            one(dependencyDescriptorFactoryMock).createDescriptor(testId, false, true, true, clientModule.confs,
                    [], [(ClientModule.CLIENT_MODULE_KEY): testId]); will(returnValue(expectedDependencyDescriptor))
        }

        DependencyDescriptor dependencyDescriptor
        println clientModule.dependencyFactory.getClass()
        clientModule.dependencies(testDependencyUserDescription)
        assert clientModule.createDepencencyDescriptor().is(expectedDependencyDescriptor)
        ModuleDescriptor moduleDescriptor = testModuleRegistry[testId]
        assert moduleDescriptor
        assert moduleDescriptor.moduleRevisionId == expectedDependencyDescriptor.dependencyRevisionId
        assert moduleDescriptor.dependencies[0].is(testDependencyDescriptor)
    }

    @Test (expected = UnsupportedOperationException)
    public void testUnsupportedOperationsClientModule() {
        clientModule.clientModule(['a'], 'a')
    }

    @Test (expected = UnsupportedOperationException)
    public void testUnsupportedOperationsDependencies() {
        clientModule.dependencies(['a'], 'a')
    }

    @Test (expected = UnsupportedOperationException)
    public void testUnsupportedOperationsDependency() {
        clientModule.dependency(['a'], 'a')
    }


}
