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

import static org.hamcrest.Matchers.*;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class ArtifactDependenciesIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void dependencyReportWithConflicts() {
        File buildFile = getTestBuildFile("projectWithConflicts.gradle");
        getTestBuildFile("projectA-1.2-ivy.xml");
        getTestBuildFile("projectB-1.5-ivy.xml");
        getTestBuildFile("projectB-2.1.5-ivy.xml");
        testFile("projectA-1.2.jar").touch();
        testFile("projectB-1.5.jar").touch();
        testFile("projectB-2.1.5.jar").touch();

        usingBuildFile(buildFile).withDependencyList().run();
    }

    @Test
    public void canNestModules() throws IOException {
        File buildFile = getTestBuildFile("projectWithNestedModules.gradle");
        testFile("projectA-1.2.jar").touch();
        testFile("projectB-1.5.jar").touch();
        testFile("projectC-2.0.jar").touch();

        usingBuildFile(buildFile).run();
    }

    @Test
    public void reportsUnknownDependencyError() {
        File buildFile = getTestBuildFile("projectWithUnknownDependency.gradle");
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration 'compile'"));
        failure.assertThatCause(containsString("unresolved dependency: test#projectA;1.2: not found"));
        failure.assertThatCause(containsString("unresolved dependency: test#projectB;2.1.5: not found"));
    }

    @Test
    public void reportsProjectDependsOnSelfError() {
        TestFile buildFile = testFile("build.gradle");
        buildFile.writelns(
                "configurations { compile }",
                "dependencies { compile project(':') }",
                "defaultTasks 'listJars'",
                "task listJars << { configurations.compile.each { println it } }"
        );
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration 'compile'"));
        failure.assertThatCause(containsString("a module is not authorized to depend on itself"));
    }

    @Test
    public void canSpecifyProducerTasksForFileDependency() {
        testFile("settings.gradle").write("include 'sub'");
        TestFile buildFile = testFile("build.gradle").writelns(
                "configurations { compile }",
                "dependencies { compile project(path: ':sub', configuration: 'compile') }",
                "task test(dependsOn: configurations.compile) << { assertTrue(file('sub/sub.jar').isFile()) }"
        );
        testFile("sub/build.gradle").writelns(
                "usePlugin org.gradle.api.plugins.BasePlugin",
                "configurations { compile }",
                "dependencies { compile files('sub.jar') { builtBy 'jar' } }",
                "task jar << { file('sub.jar').text = 'content' }"
        );

        usingBuildFile(buildFile).withTasks("test").run().assertTasksExecuted(":sub:jar", ":sub:uploadCompileInternal", ":test");
    }
}
