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
package org.gradle.api.internal.artifacts;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DefaultDependencyConfigurationMappingContainer;
import org.gradle.api.internal.artifacts.dependencies.AbstractDependency;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import static org.hamcrest.Matchers.sameInstance;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
abstract public class AbstractDependencyTest {
    protected static final String TEST_CONF = "conf";
    
    public static final DefaultDependencyConfigurationMappingContainer TEST_CONF_MAPPING =
        new DefaultDependencyConfigurationMappingContainer() {{
            addMasters(new DefaultConfiguration(TEST_CONF, new DefaultConfigurationContainer()));
    }};

    protected static final DefaultProject TEST_PROJECT = new DefaultProject("someName");

    protected abstract AbstractDependency getDependency();
    protected abstract void expectDescriptorBuilt(DependencyDescriptor descriptor);

    private ModuleDescriptor parentModuleDescriptorMock;

    protected JUnit4Mockery context = new JUnit4GroovyMockery();

    @Before
    public void setUp() {
        parentModuleDescriptorMock = context.mock(ModuleDescriptor.class);
    }

    @Test
    public void testGenericInit() {
        assertEquals(TEST_CONF_MAPPING, getDependency().getDependencyConfigurationMappings());
    }

    @Test public void testCreateDependencyDescriptor() {
        DependencyDescriptor expectedDependencyDescriptor = context.mock(DependencyDescriptor.class);
        expectDescriptorBuilt(expectedDependencyDescriptor);
        assertSame(expectedDependencyDescriptor, getDependency().createDependencyDescriptor(parentModuleDescriptorMock));
    }

    @Test
    public void testTransformerCanModifyIvyDescriptor() {
        final DependencyDescriptor original = context.mock(DependencyDescriptor.class, "original");
        final DependencyDescriptor transformed = context.mock(DependencyDescriptor.class, "transformed");
        final Transformer<DependencyDescriptor> transformer = context.mock(Transformer.class);

        context.checking(new Expectations() {{
            one(transformer).transform(with(sameInstance(original)));
            will(returnValue(transformed));
        }});
        expectDescriptorBuilt(original);

        getDependency().addIvyTransformer(transformer);

        DependencyDescriptor descriptor = getDependency().createDependencyDescriptor(parentModuleDescriptorMock);
        assertThat(descriptor, sameInstance(transformed));
    }

    @Test
    public void testTransformationClosureCanModifyIvyDescriptor() {
        final DependencyDescriptor original = context.mock(DependencyDescriptor.class, "original");
        final DependencyDescriptor transformed = context.mock(DependencyDescriptor.class, "transformed");

        getDependency().addIvyTransformer(HelperUtil.returns(transformed));

        expectDescriptorBuilt(original);

        DependencyDescriptor descriptor = getDependency().createDependencyDescriptor(parentModuleDescriptorMock);
        assertThat(descriptor, sameInstance(transformed));
    }

    protected ModuleDescriptor getParentModuleDescriptorMock() {
        return parentModuleDescriptorMock;
    }

    protected void setParentModuleDescriptorMock(ModuleDescriptor parentModuleDescriptorMock) {
        this.parentModuleDescriptorMock = parentModuleDescriptorMock;
    }
}
