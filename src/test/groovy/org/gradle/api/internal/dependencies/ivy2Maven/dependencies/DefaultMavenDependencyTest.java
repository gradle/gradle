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
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.gradle.util.WrapUtil;

import java.util.List;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultMavenDependencyTest {
    private static final String TEST_GROUP_ID = "groupId";
    private static final String TEST_ARTIFACT_ID = "artifactId";
    private static final String TEST_VERSION = "version";
    private static final String TEST_TYPE = "type";
    private static final String TEST_SCOPE = "scope";
    private static final String TEST_CLASSIFIER = "classifier";
    private static final boolean TEST_OPTIONAL = true;

    private List<MavenExclude> testMavenExcludes;
    private DefaultMavenDependency mavenDependency;
    private MavenExclude mavenExclude1;
    private MavenExclude mavenExclude2;

    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        mavenExclude1 = context.mock(MavenExclude.class, "exclude1");
        mavenExclude2 = context.mock(MavenExclude.class, "exclude2");
        testMavenExcludes = WrapUtil.toList(mavenExclude1, mavenExclude2);
        mavenDependency = new DefaultMavenDependency(
                TEST_GROUP_ID,
                TEST_ARTIFACT_ID,
                TEST_VERSION,
                TEST_TYPE,
                TEST_SCOPE,
                testMavenExcludes,
                TEST_OPTIONAL,
                TEST_CLASSIFIER);
    }

    @Test
    public void init() {
        assertEquals(TEST_GROUP_ID, mavenDependency.getGroupId());
        assertEquals(TEST_ARTIFACT_ID, mavenDependency.getArtifactId());
        assertEquals(TEST_VERSION, mavenDependency.getVersion());
        assertEquals(TEST_TYPE, mavenDependency.getType());
        assertEquals(TEST_SCOPE, mavenDependency.getScope());
        assertEquals(TEST_OPTIONAL, mavenDependency.isOptional());
        assertEquals(TEST_CLASSIFIER, mavenDependency.getClassifier());
        assertEquals(testMavenExcludes, mavenDependency.getMavenExcludes());
    }

    @Test
    public void xmlExcludesWithNonDefaultAndOptionalsSet() {
        StringWriter stringWriter = new StringWriter();
        final PrintWriter testWriter = new PrintWriter(stringWriter);
        context.checking(new Expectations() {{
            for (MavenExclude testMavenExclude : testMavenExcludes) {
                one(testMavenExclude).write(testWriter);
            }
        }});
        mavenDependency.write(testWriter);
        assertEquals(getExpectedXmlWithAllElementsSet(), stringWriter.toString());
    }

    @Test
    public void xmlWithoutExcludesAndWithOptionalsNotSet() {
        StringWriter stringWriter = new StringWriter();
        final PrintWriter testWriter = new PrintWriter(stringWriter);
        testMavenExcludes.clear();
        mavenDependency = new DefaultMavenDependency(
                null,
                null,
                null,
                null,
                null,
                testMavenExcludes,
                false,
                null);
        mavenDependency.write(testWriter);

        assertEquals(String.format("    <dependency>%n    </dependency>%n"),
                stringWriter.toString());
    }

    private String getExpectedXmlWithAllElementsSet() {
        String expectedXmlBegin = String.format("    <dependency>%n" +
                "      <groupId>%s</groupId>%n" +
                "      <artifactId>%s</artifactId>%n" +
                "      <version>%s</version>%n" +
                "      <scope>%s</scope>%n" +
                "      <type>%s</type>%n" +
                "      <optional>%s</optional>%n" +
                "      <classifier>%s</classifier>%n" +
                "      <excludes>%n      </excludes>%n    </dependency>%n", TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION, TEST_SCOPE,
                TEST_TYPE, TEST_OPTIONAL, TEST_CLASSIFIER);
        return expectedXmlBegin;
    }

    @Test
    public void equality() {
        assertTrue(mavenDependency.equals(new DefaultMavenDependency(
                TEST_GROUP_ID,
                TEST_ARTIFACT_ID,
                TEST_VERSION,
                TEST_TYPE,
                TEST_SCOPE,
                testMavenExcludes,
                TEST_OPTIONAL,
                TEST_CLASSIFIER)));
        assertFalse(mavenDependency.equals(new DefaultMavenDependency(
                TEST_GROUP_ID + "xxx",
                TEST_ARTIFACT_ID,
                TEST_VERSION,
                TEST_TYPE,
                TEST_SCOPE,
                testMavenExcludes,
                TEST_OPTIONAL,
                TEST_CLASSIFIER)));
    }

    @Test
    public void hashcode() {
        assertEquals(mavenDependency.hashCode(), new DefaultMavenDependency(
                TEST_GROUP_ID,
                TEST_ARTIFACT_ID,
                TEST_VERSION,
                TEST_TYPE,
                TEST_SCOPE,
                testMavenExcludes,
                TEST_OPTIONAL,
                TEST_CLASSIFIER).hashCode());
    }
}
