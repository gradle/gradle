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

package org.gradle.integtests

import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesWebQuickstartIntegrationTest {
    static final String WEB_PROJECT_NAME = 'web-project'

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void webProjectSamples() {
        File webProjectDir = new File(dist.samplesDir, "webApplication/quickstart")
        executer.inDirectory(webProjectDir).withTasks('clean', 'libs').run()

        ExecutionResult result = executer.inDirectory(webProjectDir).withTasks('clean', 'runTest').run()
        checkServletOutput(result)
        result = executer.inDirectory(webProjectDir).withTasks('clean', 'runWarTest').run()
        checkServletOutput(result)
    }

    static void checkServletOutput(ExecutionResult result) {
        assertThat(result.output, containsString('hello Gradle'))
    }
}
