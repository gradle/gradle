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
import org.gradle.util.TestFile
import org.junit.Rule

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesWebQuickstartIntegrationTest {
    static final String WEB_PROJECT_NAME = 'web-project'

    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer

    @Test
    public void webProjectSamples() {
        TestFile webProjectDir = dist.samplesDir.file('webApplication/quickstart')
        executer.inDirectory(webProjectDir).withTasks('clean', 'build').run()

        // Check contents of War
        TestFile warContents = dist.testDir.file('jar')
        webProjectDir.file("build/libs/quickstart.war").unzipTo(warContents)
        warContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'index.jsp',
                'WEB-INF/classes/org/gradle/sample/Greeter.class',
                'WEB-INF/classes/greeting.txt',
                'WEB-INF/lib/log4j-1.2.15.jar',
                'WEB-INF/lib/commons-io-1.4.jar',
        )
        
        ExecutionResult result = executer.inDirectory(webProjectDir).withTasks('clean', 'runTest').run()
        checkServletOutput(result)
        result = executer.inDirectory(webProjectDir).withTasks('clean', 'runWarTest').run()
        checkServletOutput(result)
    }

    static void checkServletOutput(ExecutionResult result) {
        assertThat(result.output, containsString('hello Gradle'))
    }
}
