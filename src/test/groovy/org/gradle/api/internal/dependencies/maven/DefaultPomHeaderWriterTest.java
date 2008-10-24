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
package org.gradle.api.internal.dependencies.maven;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.gradle.api.internal.dependencies.maven.PomWriter.NL;
import org.gradle.util.GradleVersion;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultPomHeaderWriterTest {
    private DefaultPomHeaderWriter headerWriter;
    private StringWriter stringWriter;

    @Before
    public void setUp() {
        headerWriter = new DefaultPomHeaderWriter();
        stringWriter = new StringWriter();
    }

    @Test
    public void convert() {
        String testLicenseText = "licenseText";
        String expectedHeaderText = testLicenseText + NL + expectedHeaderText();
        headerWriter.convert(testLicenseText, new PrintWriter(stringWriter));
        assertEquals(expectedHeaderText, stringWriter.toString());
    }

    @Test
    public void convertWithNullLicense() {
        headerWriter.convert(null, new PrintWriter(stringWriter));
        assertEquals(expectedHeaderText(), stringWriter.toString());
    }

    private String expectedHeaderText() {
        GradleVersion gradleVersion = new GradleVersion();
        return PomHeaderWriter.HEADER_XML + NL +
                PomHeaderWriter.GENERATE_TEXT_PRE + GradleVersion.URL + NL +
                PomHeaderWriter.GENERATE_TEXT_VERSION + gradleVersion.getVersion() + " " + gradleVersion.getBuildTime() + NL +
                PomHeaderWriter.GENERATE_TEXT_POST +
                PomHeaderWriter.HEADER_XMLNS + NL +
                PomHeaderWriter.HEADER_MAVEN + NL;
    }
}
