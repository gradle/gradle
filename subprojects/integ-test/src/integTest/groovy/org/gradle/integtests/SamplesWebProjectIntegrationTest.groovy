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

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.gradle.integtests.fixtures.Sample

/**
 * @author Hans Dockter
 */
class SamplesWebProjectIntegrationTest {
    static final String WEB_PROJECT_NAME = 'customised'

    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final Sample sample = new Sample('webApplication/customised')

    @Test
    public void webProjectSamples() {
        TestFile webProjectDir = sample.dir
        executer.inDirectory(webProjectDir).withTasks('clean', 'assemble').run()
        TestFile tmpDir = dist.testDir.file('unjar')
        webProjectDir.file("build/libs/customised-1.0.war").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'root.txt',
                'META-INF/MANIFEST.MF',
                'WEB-INF/classes/org/gradle/HelloServlet.class',
                'WEB-INF/classes/org/gradle/MyClass.class',
                'WEB-INF/lib/compile-1.0.jar',
                'WEB-INF/lib/compile-transitive-1.0.jar',
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
        TestFile webProjectDir = sample.dir
        executer.inDirectory(webProjectDir).withTasks('clean', 'runTest').run()
        checkServletOutput(webProjectDir)
        executer.inDirectory(webProjectDir).withTasks('clean', 'runWarTest').run()
        checkServletOutput(webProjectDir)
    }

    static void checkServletOutput(TestFile webProjectDir) {
        Assert.assertEquals('Hello Gradle', webProjectDir.file("build/servlet-out.txt").text)
    }
}
