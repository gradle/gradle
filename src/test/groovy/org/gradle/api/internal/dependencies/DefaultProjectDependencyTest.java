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

import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.dependencies.ivy.DependencyDescriptorFactory;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultProjectDependencyTest extends AbstractDependencyTest {
    private static final String TEST_DEPENDENCY_CONF = "depconf";

    private DefaultProjectDependency projectDependency;
    private Project project;
    private Project dependencyProject;
    private DependencyDescriptorFactory dependencyDescriptorFactoryMock;

    protected AbstractDependency getDependency() {
        return projectDependency;
    }

    protected Object getUserDescription() {
        return dependencyProject;
    }

    protected void expectDescriptorBuilt(final DependencyDescriptor descriptor) {
        context.checking(new Expectations() {{
            one(dependencyDescriptorFactoryMock).createFromProjectDependency(getParentModuleDescriptorMock(),
                    projectDependency);
            will(returnValue(descriptor));
        }});
    }

    @Before public void setUp() {
        project = HelperUtil.createRootProject(new File("root"));
        dependencyProject = HelperUtil.createRootProject(new File("dependency"));
        projectDependency = new DefaultProjectDependency(TEST_CONF_MAPPING, dependencyProject, project);
        super.setUp();
        dependencyDescriptorFactoryMock = context.mock(DependencyDescriptorFactory.class);
        projectDependency.setDependencyDescriptorFactory(dependencyDescriptorFactoryMock);
    }
    
    @Test
    public void init() {
        projectDependency = new DefaultProjectDependency(TEST_CONF_MAPPING, dependencyProject, project);
        assertTrue(projectDependency.isTransitive());
        assertNotNull(projectDependency.getExcludeRules());
        assertNotNull(projectDependency.getDependencyConfigurationMappings());
        assertEquals(dependencyProject.getName(), projectDependency.getName());
        assertEquals(dependencyProject.getGroup().toString(), projectDependency.getGroup());
        assertEquals(dependencyProject.getVersion().toString(), projectDependency.getVersion());
    }

    @Test (expected = UnknownDependencyNotation.class) public void testWithSingleString() {
        new DefaultProjectDependency(TEST_CONF_MAPPING, "string", project);
    }

    @Test (expected = UnknownDependencyNotation.class) public void testWithUnknownType() {
        new DefaultProjectDependency(TEST_CONF_MAPPING, new Point(3, 4), project);
    }


}
