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
package org.gradle.integtests;

import org.junit.Test;

public class WebProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void handlesEmptyProject() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('war')"
        );

        usingBuildFile(buildFile).withTasks("libs").run();
    }

    @Test
    public void createsAWar() {
        testFile("settings.gradle").writelns("rootProject.name = 'test'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('war')"
        );
        testFile("src/main/webapp/index.jsp").write("<p>hi</p>");

        usingBuildFile(buildFile).withTasks("libs").run();
        testFile("build/libs/test-unspecified.war").assertExists();
    }

    @Test
    public void canCustomiseWarUsingConventionProperties() {
        testFile("settings.gradle").writelns("rootProject.name = 'test'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "usePlugin('war')",
                "buildDirName = 'output'",
                "libsDirName = 'wars'",
                "archivesBaseName = 'test'",
                "version = '0.5-RC2'"
        );
        testFile("src/main/webapp/index.jsp").write("<p>hi</p>");

        usingBuildFile(buildFile).withTasks("libs").run();
        testFile("output/wars/test-0.5-RC2.war").assertExists();
    }
}
