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
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.util.HelperUtil;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.hamcrest.Matchers;

import java.io.File;
import java.awt.*;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyFactoryTest {
    private ProjectDependencyFactory projectDependencyFactory = new ProjectDependencyFactory();

    @Test
    public void testCreateDependencyWithProjectUserDescription() {
        Project expectedDescription = HelperUtil.createRootProject(new File("root2"));
        DefaultProjectDependency projectDependency = (DefaultProjectDependency)
                projectDependencyFactory.createDependency(expectedDescription);
        assertThat(projectDependency.getDependencyProject(), Matchers.equalTo(expectedDescription));
    }

    @Test (expected = IllegalDependencyNotation.class)
    public void testWithUnknownType_shouldThrowUnknownDependencyNotationEx() {
        projectDependencyFactory.createDependency(new Point(3, 4));
    }
}
