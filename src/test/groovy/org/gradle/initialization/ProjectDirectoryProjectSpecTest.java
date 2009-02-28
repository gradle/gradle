/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.util.HelperUtil;
import org.gradle.api.internal.project.ProjectIdentifier;

import java.io.File;
import java.io.IOException;

@RunWith(JMock.class)
public class ProjectDirectoryProjectSpecTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final File testDir = HelperUtil.getTestDir();
    ProjectDirectoryProjectSpec spec = new ProjectDirectoryProjectSpec(testDir);

    @Test
    public void selectsProjectWithSameProjectDir() {
        ProjectIdentifier project = project(testDir);

        assertTrue(spec.isSatisfiedBy(project));
    }

    @Test
    public void doesNotSelectProjectWithDifferentProjectDir() {
        ProjectIdentifier project = project(new File(testDir, "child"));

        assertFalse(spec.isSatisfiedBy(project));
    }

    private ProjectIdentifier project(final File testDir) {
        final ProjectIdentifier projectIdentifier = context.mock(ProjectIdentifier.class);
        context.checking(new Expectations(){{
            allowing(projectIdentifier).getProjectDir();
            will(returnValue(testDir));
        }});
        return projectIdentifier;
    }
}
