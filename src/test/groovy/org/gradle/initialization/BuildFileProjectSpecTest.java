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
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.util.HelperUtil;
import org.gradle.api.internal.project.ProjectIdentifier;

import java.io.File;

@RunWith(JMock.class)
public class BuildFileProjectSpecTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final File file = new File(HelperUtil.getTestDir(), "build");
    BuildFileProjectSpec spec = new BuildFileProjectSpec(file);

    @Test
    public void selectsProjectWithSameBuildFile() {
        ProjectIdentifier project = project(file);

        assertTrue(spec.isSatisfiedBy(project));
    }

    @Test
    public void doesNotSelectProjectWithDifferentBuildFile() {
        ProjectIdentifier project = project(new File("other"));

        assertFalse(spec.isSatisfiedBy(project));
    }

    private ProjectIdentifier project(final File buildFile) {
        final ProjectIdentifier projectIdentifier = context.mock(ProjectIdentifier.class);
        context.checking(new Expectations(){{
            allowing(projectIdentifier).getBuildFile();
            will(returnValue(buildFile));
        }});
        return projectIdentifier;
    }
}