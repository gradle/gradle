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
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;

/**
 * @author Hans Dockter
 */
public class ModuleDependencyTest extends AbstractDependencyTest {
    static final String TEST_DESCRIPTOR = "junit:junit:4.4";

    protected DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    protected DefaultDependencyDescriptor expectedDependencyDescriptor;

    private ModuleDependency moduleDependency;

    protected JUnit4Mockery context = new JUnit4Mockery();

    public AbstractDependency getDependency() {
        return moduleDependency;
    }

    protected Object getUserDescription() {
        return TEST_DESCRIPTOR;
    }

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        moduleDependency = new ModuleDependency(TEST_CONF_SET, TEST_DESCRIPTOR);
        moduleDependency.getExcludeRules().add(GUtil.map("org", "someorg", "module", "somemodule"));
        moduleDependency.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor();
    }

    @Test
    public void testInit() {
        assert !moduleDependency.isForce();
        assertNotNull(moduleDependency.getExcludeRules());
    }

    @Test (expected = UnknownDependencyNotation.class) public void testSingleString() {
        new ModuleDependency(TEST_CONF_SET, "singlestring");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testMissingVersion() {
        new ModuleDependency(TEST_CONF_SET, "junit:junit");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testArtifactNotation() {
        new ModuleDependency(TEST_CONF_SET, "junit:junit:3.8.2@jar");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testArtifactNotationWithClassifier() {
        new ModuleDependency(TEST_CONF_SET, "junit:junit:3.8.2:jdk14@jar");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testUnknownType() {
        new ModuleDependency(TEST_CONF_SET, new Point(3, 4));
    }

    @Test public void testCreateDependencyDescriptor() {
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createDescriptor(getParentModuleDescriptor(), TEST_DESCRIPTOR, moduleDependency.isForce(),
                    true, false, TEST_CONF_SET, moduleDependency.getExcludeRules().getRules());
            will(returnValue(expectedDependencyDescriptor));
        }});
        assertSame(expectedDependencyDescriptor, moduleDependency.createDepencencyDescriptor(getParentModuleDescriptor()));
    }

    @Test public void testCreateDependencyDescriptorWithChanging() {
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createDescriptor(getParentModuleDescriptor(), TEST_DESCRIPTOR, moduleDependency.isForce(),
                    true, true, TEST_CONF_SET, moduleDependency.getExcludeRules().getRules());
            will(returnValue(expectedDependencyDescriptor));
        }});
        moduleDependency.setChanging(true);
        assertSame(expectedDependencyDescriptor, moduleDependency.createDepencencyDescriptor(getParentModuleDescriptor()));
    }
}

