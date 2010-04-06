/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.logging.StandardOutputLogging;
import static org.gradle.util.Matchers.*;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

@RunWith(JMock.class)
public class TextProjectReportRendererTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    @Test
    public void writesReportToStandardOutByDefault() throws IOException {
        TextProjectReportRenderer renderer = new TextProjectReportRenderer();
        assertThat(renderer.getWriter(), sameInstance((Appendable) StandardOutputLogging.getOut()));

        renderer.complete();

        assertThat(renderer.getWriter(), sameInstance((Appendable) StandardOutputLogging.getOut()));
    }

    @Test
    public void writesReportToAFile() throws IOException {
        File outFile = new File(testDir.getDir(), "report.txt");
        TextProjectReportRenderer renderer = new TextProjectReportRenderer();
        renderer.setOutputFile(outFile);
        assertThat(renderer.getWriter(), instanceOf(FileWriter.class));

        renderer.complete();

        assertTrue(outFile.isFile());
        assertThat(renderer.getWriter(), sameInstance((Appendable) StandardOutputLogging.getOut()));
    }

    @Test
    public void writeRootProjectHeader() throws IOException {
        final Project project = context.mock(Project.class);
        StringWriter writer = new StringWriter();

        context.checking(new Expectations() {{
            allowing(project).getRootProject();
            will(returnValue(project));
        }});

        TextProjectReportRenderer renderer = new TextProjectReportRenderer(writer);
        renderer.startProject(project);
        renderer.completeProject(project);
        renderer.complete();

        assertThat(writer.toString(), containsLine("Root Project"));
    }
    
    @Test
    public void writeSubProjectHeader() throws IOException {
        final Project project = context.mock(Project.class);
        StringWriter writer = new StringWriter();

        context.checking(new Expectations() {{
            allowing(project).getRootProject();
            will(returnValue(context.mock(Project.class, "root")));
            allowing(project).getPath();
            will(returnValue("<path>"));
        }});

        TextProjectReportRenderer renderer = new TextProjectReportRenderer(writer);
        renderer.startProject(project);
        renderer.completeProject(project);
        renderer.complete();

        assertThat(writer.toString(), containsLine("Project <path>"));
    }
}
