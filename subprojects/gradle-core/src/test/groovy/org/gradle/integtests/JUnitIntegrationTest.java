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
import org.junit.runner.RunWith;

@RunWith(DistributionIntegrationTestRunner.class)
public class JUnitIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void testFailureBreaksBuild() {
        TestFile testDir = dist.getTestDir();
        TestFile buildFile = testDir.file("build.gradle");
        buildFile.writelns(
                "usePlugin('java')",
                "repositories { mavenCentral() }",
                "dependencies { testCompile 'junit:junit:4.4' }"
        );
        testDir.file("src/test/java/org/gradle/BrokenTest.java").writelns(
                "package org.gradle;",
                "public class BrokenTest {",
                "@org.junit.Test public void broken() { org.junit.Assert.fail(); }",
                "}");

        ExecutionFailure failure = executer.withTasks("build").runWithFailure();

        failure.assertHasFileName(String.format("Build file '%s'", buildFile));
        failure.assertHasDescription("Execution failed for task ':test'.");
        failure.assertThatCause(startsWith("There were failing tests."));
    }
}
