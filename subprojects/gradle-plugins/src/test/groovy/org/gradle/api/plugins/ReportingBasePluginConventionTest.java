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
package org.gradle.api.plugins;

import org.gradle.api.Project;
import static org.hamcrest.Matchers.*;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JMock.class)
public class ReportingBasePluginConventionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final ReportingBasePluginConvention convention = new ReportingBasePluginConvention(project);
    private final File buildDir = new File("build-dir");

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getBuildDir();
            will(returnValue(buildDir));
        }});
    }

    @Test
    public void defaultValues() {
        assertThat(convention.getReportsDirName(), equalTo("reports"));
    }

    @Test
    public void calculatesReportsDirFromReportsDirName() {
        context.checking(new Expectations(){{
            FileResolver fileResolver = context.mock(FileResolver.class);
            FileResolver buildDirResolver = context.mock(FileResolver.class, "buildDir");
            allowing(project).getFileResolver();
            will(returnValue(fileResolver));
            one(fileResolver).withBaseDir(buildDir);
            will(returnValue(buildDirResolver));
            one(buildDirResolver).resolve("new-reports");
            will(returnValue(new File(buildDir, "new-reports")));
        }});

        convention.setReportsDirName("new-reports");
        assertThat(convention.getReportsDir(), equalTo(new File(buildDir, "new-reports")));
    }

    @Test
    public void calculatesApiDocTitleFromProjectNameAndVersion() {
        context.checking(new Expectations(){{
            allowing(project).getName();
            will(returnValue("<name>"));
            one(project).getVersion();
            will(returnValue(Project.DEFAULT_VERSION));
        }});
        assertThat(convention.getApiDocTitle(), equalTo("<name> API"));

        context.checking(new Expectations(){{
            one(project).getVersion();
            will(returnValue("<not-the-default>"));
        }});
        assertThat(convention.getApiDocTitle(), equalTo("<name> <not-the-default> API"));
    }
}
