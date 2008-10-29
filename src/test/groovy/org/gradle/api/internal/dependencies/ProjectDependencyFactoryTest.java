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

import org.gradle.api.Project;
import org.gradle.api.dependencies.ProjectDependency;
import org.gradle.api.internal.dependencies.DefaultProjectDependency;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class ProjectDependencyFactoryTest {
    private ProjectDependencyFactory projectDependencyFactory;
    private Set<String> expectedConfs;
    private Project expectedProject;

    private JUnit4Mockery context = new JUnit4GroovyMockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    @Before
    public void setUp() {
        expectedConfs = WrapUtil.toSet("conf1");
        expectedProject = HelperUtil.createRootProject(new File("root"));
        projectDependencyFactory = new ProjectDependencyFactory();
    }

    @Test
    public void testCreateDependencyWithProjectUserDescription() {
        Project expectedDescription = HelperUtil.createRootProject(new File("root2"));
        DefaultProjectDependency projectDependency = (DefaultProjectDependency)
                projectDependencyFactory.createDependency(AbstractDependencyTest.TEST_CONF_MAPPING, expectedDescription, expectedProject);
        assertSame(expectedDescription, projectDependency.getUserDependencyDescription());
        assertSame(expectedProject, projectDependency.getProject());
        assertSame(AbstractDependencyTest.TEST_CONF_MAPPING, projectDependency.getDependencyConfigurationMappings());
    }
}
