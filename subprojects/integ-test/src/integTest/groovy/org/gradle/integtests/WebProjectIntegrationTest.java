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

import org.gradle.integtests.fixtures.AbstractIntegrationTest;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.Test;

public class WebProjectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void createsAWar() {
        testFile("settings.gradle").writelns("rootProject.name = 'test'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "apply plugin: 'war'"
        );
        testFile("src/main/webapp/index.jsp").write("<p>hi</p>");

        executer.withTasks("assemble").run();
        testFile("build/libs/test.war").assertIsFile();
    }

    @Test
    public void canCustomizeArchiveNamesUsingConventionProperties() {
        testFile("settings.gradle").writelns("rootProject.name = 'test'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "apply plugin: 'war'",
                "buildDir = 'output'",
                "base {",
                "    archivesName = 'test'",
                "    libsDirectory = layout.buildDirectory.dir('archives')",
                "}",
                "version = '0.5-RC2'"
        );
        testFile("src/main/resources/org/gradle/resource.file").write("some resource");

        executer.withTasks("assemble").run();
        testFile("output/archives/test-0.5-RC2.war").assertIsFile();
    }

    @Test
    public void generatesArtifactsWhenVersionIsEmpty() {
        testFile("settings.gradle").write("rootProject.name = 'empty'");
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "apply plugin: 'war'",
                "version = ''"
        );
        testFile("src/main/resources/org/gradle/resource.file").write("some resource");

        executer.withTasks("assemble").run();
        testFile("build/libs/empty.war").assertIsFile();
    }

}
