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

import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesWebProjectIntegrationTest {
    static final String WEB_PROJECT_NAME = 'customised'

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void webProjectSamples() {
        TestFile webProjectDir = dist.samplesDir.file("webApplication/$WEB_PROJECT_NAME")
        executer.inDirectory(webProjectDir).withTasks('clean', 'libs').run()
        TestFile tmpDir = dist.testDir.file('unjar')
        webProjectDir.file("build/libs/$WEB_PROJECT_NAME-1.0.war").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'root.txt',
                'META-INF/MANIFEST.MF',
                'WEB-INF/classes/org/HelloServlet.class',
                'WEB-INF/classes/org/MyClass.class',
                'WEB-INF/lib/compile-1.0.jar',
                'WEB-INF/lib/runtime-1.0.jar',
                'WEB-INF/lib/additional-1.0.jar',
                'WEB-INF/lib/otherLib-1.0.jar',
                'WEB-INF/additional.xml',
                'WEB-INF/webapp.xml',
                'WEB-INF/web.xml',
                'webapp.html')
    }

    @Test
    public void checkJettyPlugin() {
        TestFile webProjectDir = dist.samplesDir.file("webApplication/$WEB_PROJECT_NAME")
        executer.inDirectory(webProjectDir).withTasks('clean', 'runTest').run()
        checkServletOutput(webProjectDir)
        executer.inDirectory(webProjectDir).withTasks('clean', 'runWarTest').run()
        checkServletOutput(webProjectDir)
    }

    static void checkServletOutput(TestFile webProjectDir) {
        Assert.assertEquals('Hello Gradle', webProjectDir.file("build/servlet-out.txt").text)
    }
}
