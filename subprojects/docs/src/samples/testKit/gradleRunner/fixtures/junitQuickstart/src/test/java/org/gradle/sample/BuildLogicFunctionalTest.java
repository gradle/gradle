/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.sample;

// START SNIPPET functional-test-junit-fixture
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.fixtures.JUnitFunctionalTest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

public class BuildLogicFunctionalTest extends JUnitFunctionalTest {
    @Test
    public void testHelloWorldTask() {
        String buildFileContent = "task helloWorld {" +
            "    doLast {" +
            "        println 'Hello world!'" +
            "    }" +
            "}";
        getBuildFile().setText(buildFileContent);

        BuildResult result = succeeds("helloWorld");

        assertTrue(result.getOutput().contains("Hello world!"));
        assertEquals(result.task(":helloWorld").getOutcome(), SUCCESS);
    }
}
// END SNIPPET functional-test-junit-fixture
