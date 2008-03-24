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

/**
* @author Hans Dockter
*/
class ModuleDependencyTest extends GroovyTestCase {
    static final String TEST_CONF = "conf"
    static final Set TEST_CONF_SET = [TEST_CONF]
    static final String TEST_ORG = "org.springframework"
    static final String TEST_NAME = "spring"
    static final String TEST_VERSION = "2.5"
    static final DefaultProject TEST_PROJECT = new DefaultProject()
    static final ModuleRevisionId TEST_MODULE_REVISION_ID = new ModuleRevisionId(new ModuleId(TEST_ORG, TEST_NAME), TEST_VERSION)
    static final String TEST_DESCRIPTOR = "$TEST_ORG:$TEST_NAME:$TEST_VERSION"

    void testModuleDependency() {
        ModuleDependency moduleDependency = new ModuleDependency(TEST_CONF_SET, TEST_DESCRIPTOR, TEST_PROJECT)
        assertEquals(TEST_CONF_SET, moduleDependency.confs)
        assertEquals(TEST_DESCRIPTOR, moduleDependency.userDependencyDescription)
        assertEquals(TEST_PROJECT, moduleDependency.project)
    }

    void testValidation() {
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "singlestring", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "junit:junit", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, "junit:junit:3.8.2:jar", TEST_PROJECT)
        }
        shouldFail(InvalidUserDataException) {
            new ModuleDependency(TEST_CONF_SET, new Point(3, 4), TEST_PROJECT)
        }
    }

    void testCreateDependencyDescriptorWithString() {
        checkDescriptor(new ModuleDependency(TEST_CONF_SET, TEST_DESCRIPTOR, TEST_PROJECT).createDepencencyDescriptor())
    }

    void testCreateDependencyDescriptorWithExclude() {
        String expectedOrg = 'org'
        String expectedModule = 'module'
        String expectedOrg2 = 'org2'
        String expectedModule2 = 'module2'
        ModuleDependency moduleDependency = new ModuleDependency(TEST_CONF_SET, TEST_DESCRIPTOR, TEST_PROJECT)
        moduleDependency.exclude(org: expectedOrg, module: expectedModule)
        moduleDependency.exclude(org: expectedOrg2, module: expectedModule2)
        DependencyDescriptor dependencyDescriptor = checkDescriptor(moduleDependency.createDepencencyDescriptor())
        assertEquals(2, dependencyDescriptor.getExcludeRules(TEST_CONF).size())
        assertEquals(dependencyDescriptor.getExcludeRules(TEST_CONF)[0].getAttribute(IvyPatternHelper.ORGANISATION_KEY),
                expectedOrg)
        assertEquals(dependencyDescriptor.getExcludeRules(TEST_CONF)[0].getAttribute(IvyPatternHelper.MODULE_KEY),
                expectedModule)
        assertEquals(dependencyDescriptor.getExcludeRules(TEST_CONF)[1].getAttribute(IvyPatternHelper.ORGANISATION_KEY),
                expectedOrg2)
        assertEquals(dependencyDescriptor.getExcludeRules(TEST_CONF)[1].getAttribute(IvyPatternHelper.MODULE_KEY),
                expectedModule2)
    }

    private DependencyDescriptor checkDescriptor(DependencyDescriptor dependencyDescriptor) {
        assertEquals(TEST_MODULE_REVISION_ID, dependencyDescriptor.dependencyRevisionId)
        assertTrue(dependencyDescriptor.isTransitive())
        assertEquals(1, dependencyDescriptor.getDependencyConfigurations(TEST_CONF).size())
        assertEquals('default', dependencyDescriptor.getDependencyConfigurations(TEST_CONF)[0])
        assert !dependencyDescriptor.getAllDependencyArtifacts()
        dependencyDescriptor
    }
}

