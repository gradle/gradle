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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.dependencies.DependencyArtifact;
import org.gradle.api.internal.dependencies.ivyservice.DependencyDescriptorFactory;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultModuleDependencyTest extends AbstractDescriptorDependencyTest {
    static final String TEST_GROUP = "org.gradle";
    static final String TEST_NAME = "gradle-core";
    static final String TEST_VERSION = "4.4-beta2";
    static final String TEST_TYPE = "mytype";
    static final String TEST_CLASSIFIER = "jdk-1.4";
    static final String TEST_MODULE_DESCRIPTOR = String.format("%s:%s:%s", TEST_GROUP, TEST_NAME, TEST_VERSION);
    static final String TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + ":" + TEST_CLASSIFIER;
    static final String TEST_ARTIFACT_DESCRIPTOR = TEST_MODULE_DESCRIPTOR + "@" + TEST_TYPE;
    static final String TEST_ARTIFACT_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + String.format(":%s@%s", TEST_CLASSIFIER, TEST_TYPE);

    protected DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    protected DefaultDependencyDescriptor expectedDependencyDescriptor;

    private DefaultModuleDependency moduleDependency;

    public AbstractDescriptorDependency getDependency() {
        return moduleDependency;
    }

    protected Object getUserDescription() {
        return TEST_MODULE_DESCRIPTOR;
    }

    protected void expectDescriptorBuilt(final DependencyDescriptor descriptor) {
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createFromModuleDependency(getParentModuleDescriptorMock(),
                    moduleDependency);
            will(returnValue(descriptor));
        }});
    }

    @Before public void setUp() {
        moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR);
        context.setImposteriser(ClassImposteriser.INSTANCE);
        super.setUp();
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        moduleDependency.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor();
    }

    @Test
    public void testInit() {
        moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR);
        assert !moduleDependency.isForce();
        assertNotNull(moduleDependency.getExcludeRules());
        assertNotNull(moduleDependency.getDependencyConfigurationMappings());
    }

    @Test (expected = UnknownDependencyNotation.class) public void testSingleString() {
        new DefaultModuleDependency(TEST_CONF_MAPPING, "singlestring");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testMissingVersion() {
        new DefaultModuleDependency(TEST_CONF_MAPPING, "junit:junit");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testUnknownType() {
        new DefaultModuleDependency(TEST_CONF_MAPPING, new Point(3, 4));
    }

    @Test public void testWithModuleUserDescription() {
        moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR);
        checkCommonModuleProperties();
        assertTrue(moduleDependency.isTransitive());
    }

    @Test public void testWithArtifactUserDescription() {
        moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, TEST_ARTIFACT_DESCRIPTOR);
        checkCommonModuleProperties();
        assertFalse(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().get(0);
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(null, artifact.getClassifier());
    }

    @Test public void testWithModuleUserDescriptionWithClassifier() {
        moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER);
        checkCommonModuleProperties();
        assertTrue(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().get(0);
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getType());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    @Test public void testWithArtifactUserDescriptionWithClassifier() {
        moduleDependency = new DefaultModuleDependency(TEST_CONF_MAPPING, TEST_ARTIFACT_DESCRIPTOR_WITH_CLASSIFIER);
        checkCommonModuleProperties();
        assertFalse(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().get(0);
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(TEST_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    private void checkCommonModuleProperties() {
        assertEquals(TEST_GROUP, moduleDependency.getGroup());
        assertEquals(TEST_NAME, moduleDependency.getName());
        assertEquals(TEST_VERSION, moduleDependency.getVersion());
        assertFalse(moduleDependency.isForce());
        assertFalse(moduleDependency.isChanging());
    }
}

