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

import java.awt.Point
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.Dependency
import org.gradle.api.internal.dependencies.DependencyFactory
import org.gradle.api.internal.dependencies.TestDependencyImplInteger
import org.gradle.api.internal.dependencies.TestDependencyImplString
import org.gradle.api.internal.project.DefaultProject
import org.junit.Before
import static org.junit.Assert.*
import org.junit.Test;

/**
 * @author Hans Dockter
 */
class DependencyFactoryTest {
    static final String TEST_CONFIGURATION = 'testconf'
    static final Set TEST_CONFIGURATION_SET = [TEST_CONFIGURATION]
    List testDependencyImplementations

    DependencyFactory dependencyFactory

    DefaultProject project

    @Before public void setUp() {
        project = new DefaultProject()
        dependencyFactory = new DependencyFactory([TestDependencyImplInteger, TestDependencyImplString])
    }

    @Test public void testCreateDependencyWithValidDescription() {
        TestDependencyImplInteger dependencyImplInteger = dependencyFactory.createDependency(
                TEST_CONFIGURATION_SET, new Integer(5), project)
        assertEquals(TEST_CONFIGURATION_SET, dependencyImplInteger.confs)
        assertEquals(new Integer(5), dependencyImplInteger.userDependencyDescription)
        assertSame(project, dependencyImplInteger.project)
        assertTrue dependencyImplInteger.initialized

        TestDependencyImplString dependencyImplString = dependencyFactory.createDependency(
                TEST_CONFIGURATION_SET, 'somestring', project)
        assertEquals(TEST_CONFIGURATION_SET, dependencyImplString.confs)
        assertEquals('somestring', dependencyImplString.userDependencyDescription)
        assertSame(project, dependencyImplString.project)
        assertTrue dependencyImplString.initialized
    }

    @Test (expected = InvalidUserDataException) public void testCreateDependencyWithInValidDescription() {
        dependencyFactory.createDependency(TEST_CONFIGURATION_SET, new Point(3, 4), project)
    }

    @Test public void testCreateDependencyWithDependencyObject() {
        TestDependencyImplInteger testDependency = new TestDependencyImplInteger()
        assert !testDependency.confs
        assertNull testDependency.project

        DefaultProject project = new DefaultProject()
        Dependency dependency = dependencyFactory.createDependency(TEST_CONFIGURATION_SET, testDependency, project)
        assert dependency.is(testDependency)
        assert testDependency.project.is(project)
        assert testDependency.confs.is(TEST_CONFIGURATION_SET)
        assertTrue testDependency.initialized

    }
}
