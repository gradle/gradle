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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.Project;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.HelperUtil;
import org.gradle.util.GUtil;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

import java.io.File;
import java.awt.*;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultProjectDependencyFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();
    
    private ProjectDependencyFactory projectDependencyFactory = new DefaultProjectDependencyFactory();
    private ProjectFinder projectFinder = context.mock(ProjectFinder.class);

    @Test
    public void testCreateProjectDependencyWithStringNotation() {
        final String somePath = ":path";
        final ProjectInternal projectDummy = context.mock(ProjectInternal.class);
        context.checking(new Expectations() {{
            allowing(projectFinder).getProject(somePath);
            will(returnValue(projectDummy));
        }});
        DefaultProjectDependency projectDependency = (DefaultProjectDependency)
                projectDependencyFactory.createProject(projectFinder, somePath);
        assertThat((ProjectInternal) projectDependency.getDependencyProject(), equalTo(projectDummy));
    }

    @Test
    public void testCreateProjectDependencyWithMapNotation() {
        boolean expectedTransitive = false;
        final Map<String, Object> mapNotation = GUtil.map("path", ":path", "configuration", "conf", "transitive", expectedTransitive);
        final ProjectInternal projectDummy = context.mock(ProjectInternal.class);
        context.checking(new Expectations() {{
            allowing(projectFinder).getProject((String) mapNotation.get("path"));
            will(returnValue(projectDummy));
        }});
        DefaultProjectDependency projectDependency = (DefaultProjectDependency)
                projectDependencyFactory.createProject(projectFinder, mapNotation);
        assertThat((ProjectInternal) projectDependency.getDependencyProject(), equalTo(projectDummy));
        assertThat(projectDependency.getDependencyConfiguration(), equalTo(mapNotation.get("configuration")));
        assertThat(projectDependency.isTransitive(), equalTo(expectedTransitive));
    }

    @Test (expected = IllegalDependencyNotation.class)
    public void testWithUnknownType_shouldThrowUnknownDependencyNotationEx() {
        projectDependencyFactory.createProject(projectFinder, new Point(3, 4));
    }
}
