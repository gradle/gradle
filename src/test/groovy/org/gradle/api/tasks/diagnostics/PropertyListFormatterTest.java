/*
 * Copyright 2008 the original author or authors.
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

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.Project;

import java.io.PrintStream;

@RunWith(JMock.class)
public class PropertyListFormatterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private PrintStream out;
    private PropertyListFormatter formatter;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        out = context.mock(PrintStream.class);
        formatter = new PropertyListFormatter(out);
    }


    @Test
    public void writesStartProject() {
        final Project project = context.mock(Project.class);
        context.checking(new Expectations() {{
            allowing(project).getPath();
            will(returnValue("<path>"));

            allowing(out).println();
            one(out).println("Project <path>");
            allowing(out).println(with(any(String.class)));
        }});

        formatter.startProject(project);
    }

    @Test
    public void writesProperty() {
        context.checking(new Expectations() {{
            one(out).println("prop: value");
        }});

        formatter.addProperty("prop", "value");
    }
}
