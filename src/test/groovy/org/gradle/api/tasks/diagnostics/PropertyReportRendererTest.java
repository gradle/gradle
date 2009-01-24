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

import static org.gradle.util.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.StringWriter;

public class PropertyReportRendererTest {
    private StringWriter out;
    private PropertyReportRenderer formatter;

    @Before
    public void setUp() {
        out = new StringWriter();
        formatter = new PropertyReportRenderer(out);
    }

    @Test
    public void writesProperty() {
        formatter.addProperty("prop", "value");

        assertThat(out.toString(), containsLine("prop: value"));
    }
}
