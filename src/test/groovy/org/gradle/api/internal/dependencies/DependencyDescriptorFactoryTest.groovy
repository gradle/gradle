/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.dependencies.ModuleDependency
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.DependencyManager
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class DependencyDescriptorFactoryTest extends GroovyTestCase {
    static final String TEST_CONF = "conf"
    static final Set TEST_CONF_SET = [TEST_CONF]
    static final List TEST_EXCLUDE_RULES = [HelperUtil.getTestExcludeRules()]
    static final Map TEST_EXTRA_ATTRIBUTES = [a: 'b']
    static final String TEST_ORG = "org.springframework"
    static final String TEST_NAME = "spring"
    static final String TEST_VERSION = "2.5"
    static final String TEST_CLASSIFIER = "testclassifier"
    static final String TEST_DESCRIPTOR = "$TEST_ORG:$TEST_NAME:$TEST_VERSION"
    static final String TEST_DESCRIPTOR_WITH_CLASSIFIER = "$TEST_DESCRIPTOR:$TEST_CLASSIFIER"
    static final boolean TEST_FORCE = true
    static final boolean TEST_TRANSITIVE = true
    static final boolean TEST_CHANGING = true

    DependencyDescriptorFactory dependencyDescriptorFactory

    protected void setUp() {
        dependencyDescriptorFactory = new DependencyDescriptorFactory()
    }

    void testCreateDependencyDescriptor() {
        checkDescriptor(dependencyDescriptorFactory.createDescriptor(TEST_DESCRIPTOR, TEST_FORCE, TEST_TRANSITIVE,
                TEST_CHANGING, TEST_CONF_SET, TEST_EXCLUDE_RULES, TEST_EXTRA_ATTRIBUTES))
    }

    void testCreateDependencyDescriptorWithClassifier() {
        checkDescriptor(dependencyDescriptorFactory.createDescriptor(TEST_DESCRIPTOR_WITH_CLASSIFIER, TEST_FORCE,
                TEST_TRANSITIVE, TEST_CHANGING, TEST_CONF_SET, TEST_EXCLUDE_RULES, TEST_EXTRA_ATTRIBUTES),
                [(DependencyManager.CLASSIFIER): TEST_CLASSIFIER])
    }
    
    private DependencyDescriptor checkDescriptor(DependencyDescriptor dependencyDescriptor, Map extraAttributes = [:]) {
        assertEquals(DependenciesUtil.moduleRevisionId(TEST_ORG, TEST_NAME, TEST_VERSION, extraAttributes + TEST_EXTRA_ATTRIBUTES), dependencyDescriptor.dependencyRevisionId)
        assert TEST_TRANSITIVE == dependencyDescriptor.isTransitive()
        assert TEST_CHANGING == dependencyDescriptor.isChanging()
        assertEquals(1, dependencyDescriptor.getDependencyConfigurations(TEST_CONF).size())
        assertEquals('default', dependencyDescriptor.getDependencyConfigurations(TEST_CONF)[0])
        assert !dependencyDescriptor.getAllDependencyArtifacts()
        assertEquals(TEST_FORCE, dependencyDescriptor.force)
        assertEquals(TEST_EXCLUDE_RULES, dependencyDescriptor.getExcludeRules(TEST_CONF))
        dependencyDescriptor
    }

}
