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
package org.gradle.api.internal.dependencies;

import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.dependencies.AbstractDependency;
import org.gradle.api.dependencies.DependencyConfigurationMappingContainer;
import org.gradle.util.WrapUtil;
import org.gradle.util.JUnit4GroovyMockery;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.jmock.integration.junit4.JUnit4Mockery;

import java.util.Set;
import java.util.Map;
import java.util.List;

/**
 * @author Hans Dockter
 */
abstract public class AbstractDependencyTest {
    protected static final String TEST_CONF = "conf";
//    protected static final Set<String> TEST_CONF_SET = WrapUtil.toSet(TEST_CONF);
    public static final DefaultDependencyConfigurationMappingContainer TEST_CONF_MAPPING =
        new DefaultDependencyConfigurationMappingContainer() {{
            addMasters(TEST_CONF);
    }};

    protected static final DefaultProject TEST_PROJECT = new DefaultProject();

    protected abstract AbstractDependency getDependency();
    protected abstract Object getUserDescription();

    private ModuleDescriptor parentModuleDescriptorMock;

    protected JUnit4Mockery context = new JUnit4GroovyMockery();

    public void setUp() {
        parentModuleDescriptorMock = context.mock(ModuleDescriptor.class);
    }

    @Test
    public void testGenericInit() {
        assertEquals(getUserDescription(), getDependency().getUserDependencyDescription());
        assertEquals(TEST_CONF_MAPPING, getDependency().getDependencyConfigurationMappings());
    }

    protected ModuleDescriptor getParentModuleDescriptorMock() {
        return parentModuleDescriptorMock;
    }

    protected void setParentModuleDescriptorMock(ModuleDescriptor parentModuleDescriptorMock) {
        this.parentModuleDescriptorMock = parentModuleDescriptorMock;
    }
}
