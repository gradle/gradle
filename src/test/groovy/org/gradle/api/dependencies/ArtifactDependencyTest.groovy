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
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.UnknownDependencyNotation
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * @author Hans Dockter
 */
class ArtifactDependencyTest {
    static final String TEST_CONF = "conf"
    static final Set TEST_CONF_SET = [TEST_CONF]
    static final String TEST_ORG = "org.springframework"
    static final String TEST_NAME = "spring"
    static final String TEST_VERSION = "2.5"
    static final String TEST_TYPE = "jar"
    static final DefaultProject TEST_PROJECT = new DefaultProject()
    static final ModuleRevisionId TEST_MODULE_REVISION_ID = new ModuleRevisionId(new ModuleId(TEST_ORG, TEST_NAME), TEST_VERSION)
    static final String TEST_MODULE_DESCRIPTOR = "$TEST_ORG:$TEST_NAME:$TEST_VERSION"
    static final String TEST_DESCRIPTOR = "$TEST_MODULE_DESCRIPTOR@$TEST_TYPE"
    ArtifactDependency artifactDependency

    DependencyDescriptorFactory dependencyDescriptorFactoryMock

    DefaultDependencyDescriptor expectedDependencyDescriptor

    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        artifactDependency = new ArtifactDependency(TEST_CONF_SET, TEST_DESCRIPTOR, TEST_PROJECT)
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory)
        artifactDependency.dependencyDescriptorFactory = dependencyDescriptorFactoryMock
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor()
    }

    @Test public void testArtifactDependency() {
        assertEquals(TEST_CONF_SET, artifactDependency.confs)
        assertEquals(TEST_DESCRIPTOR, artifactDependency.userDependencyDescription)
        assertSame(TEST_PROJECT, artifactDependency.project)
        assert !artifactDependency.force
    }

    @Test (expected = UnknownDependencyNotation) public void testValidationWithSingleString() {
        new ArtifactDependency(TEST_CONF_SET, "singlestring", TEST_PROJECT)
    }

    @Test (expected = UnknownDependencyNotation) public void testValidationWithMissingVersion() {
        new ArtifactDependency(TEST_CONF_SET, "junit:junit", TEST_PROJECT)
    }

    @Test (expected = UnknownDependencyNotation) public void testValidationWithMissingArtifactDescriptor() {
        new ArtifactDependency(TEST_CONF_SET, "junit:junit:3.8.2", TEST_PROJECT)
    }

    @Test (expected = UnknownDependencyNotation) public void testValidationWithWrongArtifactSeparator() {
        new ArtifactDependency(TEST_CONF_SET, "junit:junit:3.8.2:jdk1.4", TEST_PROJECT)
    }

    @Test (expected = UnknownDependencyNotation) public void testValidationWithUnknownType() {
        new ArtifactDependency(TEST_CONF_SET, new Point(3, 4), TEST_PROJECT)
    }


    @Test public void testCreateDependencyDescriptor() {
        context.checking {
            one(dependencyDescriptorFactoryMock).createDescriptor(TEST_MODULE_DESCRIPTOR, artifactDependency.force,
                    false, false, TEST_CONF_SET, []); will(returnValue(expectedDependencyDescriptor))
        }
        assert expectedDependencyDescriptor.is(artifactDependency.createDepencencyDescriptor())
        DependencyArtifactDescriptor artifactDescriptor = expectedDependencyDescriptor.getAllDependencyArtifacts()[0]
        assert artifactDescriptor.name == expectedDependencyDescriptor.dependencyRevisionId.name
        assertEquals('jar', artifactDescriptor.ext)
        assertEquals('jar', artifactDescriptor.type)
        assertEquals([TEST_CONF], artifactDescriptor.getConfigurations() as List)
    }

}
