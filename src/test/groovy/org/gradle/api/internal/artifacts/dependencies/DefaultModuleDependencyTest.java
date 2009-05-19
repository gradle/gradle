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

package org.gradle.api.internal.artifacts.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultModuleDependencyTest extends AbstractDependencyTest {
    private static final String TEST_GROUP = "org.gradle";
    private static final String TEST_NAME = "gradle-core";
    private static final String TEST_VERSION = "4.4-beta2";

    protected DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    protected DefaultDependencyDescriptor expectedDependencyDescriptor;

    private DefaultModuleDependency moduleDependency;

    public AbstractDependency getDependency() {
        return moduleDependency;
    }

    protected AbstractDependency createDependency(String group, String name, String version) {
        return new DefaultModuleDependency(group, name, version);
    }

    protected AbstractDependency createDependency(String group, String name, String version, String dependencyConfiguration) {
        return (DefaultModuleDependency) new DefaultModuleDependency(group, name, version, dependencyConfiguration);
    }

    @Before public void setUp() {
        moduleDependency = new DefaultModuleDependency(TEST_GROUP, TEST_NAME, TEST_VERSION);
        context.setImposteriser(ClassImposteriser.INSTANCE);
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        expectedDependencyDescriptor = HelperUtil.getTestDescriptor();
    }

    @Test
    public void init() {
        assertThat(moduleDependency.getGroup(), equalTo(TEST_GROUP));
        assertThat(moduleDependency.getName(), equalTo(TEST_NAME));
        assertThat(moduleDependency.getVersion(), equalTo(TEST_VERSION));
        assertThat(moduleDependency.isChanging(), equalTo(false));
        assertThat(moduleDependency.isForce(), equalTo(false));
        assertThat(moduleDependency.isTransitive(), equalTo(true));
        assertThat(moduleDependency.getVersion(), equalTo(TEST_VERSION));
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithNullName_shouldThrowInvalidUserDataEx() {
        new DefaultModuleDependency(TEST_GROUP, null, TEST_VERSION);
    }
    
    @Test
    public void contentEqualsWithEqualDependencies() {
        DefaultModuleDependency dependency1 = createModuleDependency();
        DefaultModuleDependency dependency2 = createModuleDependency();
        assertThat(dependency1.contentEquals(dependency2), equalTo(true));
    }

    @Test
    public void contentEqualsWithNonEqualDependencies() {
        DefaultModuleDependency dependency1 = createModuleDependency();
        DefaultModuleDependency dependency2 = createModuleDependency();
        dependency2.setTransitive(!dependency1.isTransitive());
        assertThat(dependency1.contentEquals(dependency2), equalTo(false));
    }

    @Test
    public void copy() {
        DefaultModuleDependency dependency = createModuleDependency();
        DefaultModuleDependency copiedDependency = dependency.copy();
        assertDeepCopy(dependency, copiedDependency);
    }

    private DefaultModuleDependency createModuleDependency() {
        DefaultModuleDependency moduleDependency = new DefaultModuleDependency("group", "name", "version");
        moduleDependency.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"));
        return moduleDependency;
    }
}

