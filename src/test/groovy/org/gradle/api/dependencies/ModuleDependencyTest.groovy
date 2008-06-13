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

import java.awt.Point
import org.apache.ivy.core.IvyPatternHelper
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.dependencies.DependenciesUtil
import org.gradle.api.DependencyManager
import groovy.mock.interceptor.MockFor
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory

/**
 * @author Hans Dockter
 */
class ModuleDependencyTest extends GroovyTestCase {
    static final Set TEST_CONF_SET = ['something']
    static final DefaultProject TEST_PROJECT = new DefaultProject()
    static final String TEST_DESCRIPTOR = "junit:junit:4.4"

    MockFor dependencyDescriptorFactoryMock
    ModuleDependency moduleDependency

    protected void setUp() {
        moduleDependency = new ModuleDependency(TEST_CONF_SET, TEST_DESCRIPTOR, TEST_PROJECT)
        dependencyDescriptorFactoryMock = new MockFor(DependencyDescriptorFactory)
        moduleDependency.dependencyDescriptorFactory = [:] as DependencyDescriptorFactory
    }

    void testModuleDependency() {
        assertEquals(TEST_CONF_SET, moduleDependency.confs)
        assertEquals(TEST_DESCRIPTOR, moduleDependency.userDependencyDescription)
        assertEquals(TEST_PROJECT, moduleDependency.project)
        assert !moduleDependency.force
    }

    void testValidation() {
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "singlestring", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "junit:junit", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "junit:junit:3.8.2@jar", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "junit:junit:3.8.2:jdk14@jar", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, new Point(3, 4), TEST_PROJECT)
        }
    }

    void testCreateDependencyDescriptor() {
        dependencyDescriptorFactoryMock.demand.createDescriptor(1..1) {String descriptor, boolean force, boolean transitive,
                                                                       boolean changing, Set confs, List excludeRules ->
            assertEquals(TEST_DESCRIPTOR, descriptor)
            assertEquals(moduleDependency.force, force)
            assert transitive
            assert !changing
            assertEquals(TEST_CONF_SET, confs)
            assert excludeRules.is(moduleDependency.excludeRules)
        }
        dependencyDescriptorFactoryMock.use(moduleDependency.dependencyDescriptorFactory) {
            moduleDependency.createDepencencyDescriptor()
        }
    }

    void testExclude() {
            String expectedOrg = 'org'
            String expectedModule = 'module'
            String expectedOrg2 = 'org2'
            String expectedModule2 = 'module2'
            ModuleDependency moduleDependency = new ModuleDependency(TEST_CONF_SET, TEST_DESCRIPTOR, TEST_PROJECT)
            moduleDependency.exclude(org: expectedOrg, module: expectedModule)
            moduleDependency.exclude(org: expectedOrg2, module: expectedModule2)
            moduleDependency.force = true
            assertEquals(2, moduleDependency.excludeRules.size())
            assertEquals(moduleDependency.excludeRules[0].getAttribute(IvyPatternHelper.ORGANISATION_KEY),
                    expectedOrg)
            assertEquals(moduleDependency.excludeRules[0].getAttribute(IvyPatternHelper.MODULE_KEY),
                    expectedModule)
            assertEquals(moduleDependency.excludeRules[1].getAttribute(IvyPatternHelper.ORGANISATION_KEY),
                    expectedOrg2)
            assertEquals(moduleDependency.excludeRules[1].getAttribute(IvyPatternHelper.MODULE_KEY),
                    expectedModule2)
        }


    
}

