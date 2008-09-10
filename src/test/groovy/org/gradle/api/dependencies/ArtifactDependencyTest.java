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

package org.gradle.api.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class ArtifactDependencyTest extends AbstractDependencyTest {
    private static final String TEST_ORG = "org.springframework";
    private static final String TEST_NAME = "spring";
    private static final String TEST_VERSION = "2.5";
    private static final String TEST_TYPE = "jar";
//    private static final ModuleRevisionId TEST_MODULE_REVISION_ID =
//            ModuleRevisionId.newInstance(TEST_ORG, TEST_NAME, TEST_VERSION);
    private static final String TEST_MODULE_DESCRIPTOR = TEST_ORG + ":" + TEST_NAME + ":" + TEST_VERSION;
    private static final String TEST_DESCRIPTOR = TEST_MODULE_DESCRIPTOR + "@" + TEST_TYPE;
    private ArtifactDependency artifactDependency;

    private DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    private DefaultDependencyDescriptor expectedDependencyDescriptor;

    private JUnit4Mockery context = new JUnit4GroovyMockery();

    protected AbstractDependency getDependency() {
        return artifactDependency;
    }

    protected Object getUserDescription() {
        return TEST_DESCRIPTOR;
    }

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        artifactDependency = new ArtifactDependency(TEST_CONF_SET, TEST_DESCRIPTOR);
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        artifactDependency.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor();
    }

    @Test public void testArtifactDependency() {
        assert !artifactDependency.isForce();
    }

    @Test (expected = UnknownDependencyNotation.class) public void testValidationWithSingleString() {
        new ArtifactDependency(TEST_CONF_SET, "singlestring");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testValidationWithMissingVersion() {
        new ArtifactDependency(TEST_CONF_SET, "junit:junit");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testValidationWithMissingArtifactDescriptor() {
        new ArtifactDependency(TEST_CONF_SET, "junit:junit:3.8.2");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testValidationWithWrongArtifactSeparator() {
        new ArtifactDependency(TEST_CONF_SET, "junit:junit:3.8.2:jdk1.4");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testValidationWithUnknownType() {
        new ArtifactDependency(TEST_CONF_SET, new Point(3, 4));
    }


    @Test public void testCreateDependencyDescriptor() {
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createDescriptor(getParentModuleDescriptor(), TEST_MODULE_DESCRIPTOR, artifactDependency.isForce(),
                    false, false, TEST_CONF_SET, new ArrayList<ExcludeRule>()); will(returnValue(expectedDependencyDescriptor));  
        }});
        assertSame(expectedDependencyDescriptor, artifactDependency.createDepencencyDescriptor(getParentModuleDescriptor()));
        DependencyArtifactDescriptor artifactDescriptor = expectedDependencyDescriptor.getAllDependencyArtifacts()[0];
        assertEquals(expectedDependencyDescriptor.getDependencyRevisionId().getName(), artifactDescriptor.getName());
        assertEquals("jar", artifactDescriptor.getExt());
        assertEquals("jar", artifactDescriptor.getType());
        assertArrayEquals(WrapUtil.toArray(TEST_CONF), artifactDescriptor.getConfigurations());
    }

}
