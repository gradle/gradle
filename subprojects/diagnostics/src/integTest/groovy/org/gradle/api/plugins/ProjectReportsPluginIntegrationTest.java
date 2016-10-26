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

import org.gradle.integtests.fixtures.AbstractIntegrationTest;
import org.gradle.integtests.fixtures.executer.ExecutionResult;
import org.gradle.util.TextUtil;
import org.junit.Test;

public class ProjectReportsPluginIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void generatesReportFilesToReportsDirectory() {
        applyProjectReportPlugin();
        inTestDirectory().withTasks("projectReport").run();

        testFile("build/reports/project/dependencies.txt").assertExists();
        testFile("build/reports/project/properties.txt").assertExists();
        testFile("build/reports/project/tasks.txt").assertExists();
        testFile("build/reports/project/dependencies").assertIsDir();
    }

    private void applyProjectReportPlugin() {
        testFile("build.gradle").writelns(
            "apply plugin: 'project-report'"
        );
    }

    @Test
    public void printsLinkToDefaultDependencyReport() {
        applyProjectReportPlugin();

        ExecutionResult executionResult = inTestDirectory().withTasks("dependencyReport").run();

        executionResult.assertOutputContains("See the report at: file://" + TextUtil.normaliseFileSeparators(testFile("build/reports/project/dependencies.txt").getAbsolutePath()));
    }

    @Test
    public void printsLinkToCustomDependencyReport() {
        applyProjectReportPluginWithCustomProjectReportsDirectory();

        ExecutionResult executionResult = inTestDirectory().withTasks("dependencyReport").run();

        executionResult.assertOutputContains("See the report at: file://" + TextUtil.normaliseFileSeparators(testFile("build/reports/custom/dependencies.txt").getAbsolutePath()));
    }

    private void applyProjectReportPluginWithCustomProjectReportsDirectory() {
        testFile("build.gradle").writelns(
            "apply plugin: 'project-report'",
            "projectReportDirName = 'custom'"
        );
    }

    @Test
    public void printsLinkToDefaultTaskReport() {
        applyProjectReportPlugin();

        ExecutionResult executionResult = inTestDirectory().withTasks("taskReport").run();

        executionResult.assertOutputContains("See the report at: file://" + TextUtil.normaliseFileSeparators(testFile("build/reports/project/tasks.txt").getAbsolutePath()));
    }

    @Test
    public void printsLinkToCustomTaskReport() {
        applyProjectReportPluginWithCustomProjectReportsDirectory();

        ExecutionResult executionResult = inTestDirectory().withTasks("taskReport").run();

        executionResult.assertOutputContains("See the report at: file://" + TextUtil.normaliseFileSeparators(testFile("build/reports/custom/tasks.txt").getAbsolutePath()));
    }

    @Test
    public void printsLinkToDefaultPropertyReport() {
        applyProjectReportPlugin();

        ExecutionResult executionResult = inTestDirectory().withTasks("propertyReport").run();

        executionResult.assertOutputContains("See the report at: file://" + TextUtil.normaliseFileSeparators(testFile("build/reports/project/properties.txt").getAbsolutePath()));
    }

    @Test
    public void printsLinkToCustomPropertyReport() {
        applyProjectReportPluginWithCustomProjectReportsDirectory();

        ExecutionResult executionResult = inTestDirectory().withTasks("propertyReport").run();

        executionResult.assertOutputContains("See the report at: file://" + TextUtil.normaliseFileSeparators(testFile("build/reports/custom/properties.txt").getAbsolutePath()));
    }
}
