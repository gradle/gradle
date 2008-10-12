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
package org.gradle.api.internal.dependencies.ivy2Maven.dependencies;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultMavenExcludeTest {
    private DefaultMavenExclude mavenExclude;
    private static final String TEST_GROUPID = "groupid";
    private static final String TEST_ARTIFACTID = "artifactid";

    @Before
    public void setUp() {
        mavenExclude = new DefaultMavenExclude(TEST_GROUPID, TEST_ARTIFACTID);
    }

    @Test
    public void init() {
        assertEquals(TEST_GROUPID, mavenExclude.getGroupId());
        assertEquals(TEST_ARTIFACTID, mavenExclude.getArtifactId());
    }

    @Test
    public void equality() {
        assertTrue(mavenExclude.equals(new DefaultMavenExclude(TEST_GROUPID, TEST_ARTIFACTID)));
    }

    @Test
    public void testHashcode() {
        assertEquals(mavenExclude.hashCode(), new DefaultMavenExclude(TEST_GROUPID, TEST_ARTIFACTID).hashCode());
    }

    @Test
    public void write() {
        StringWriter stringWriter = new StringWriter();
        mavenExclude.write(new PrintWriter(stringWriter));
        assertEquals(String.format("      <exclude>%n        <groupId>%s</groupId>%n        <artifactId>%s</artifactId>%n      </exclude>%n",
                TEST_GROUPID, TEST_ARTIFACTID),
                stringWriter.toString());
    }
}
