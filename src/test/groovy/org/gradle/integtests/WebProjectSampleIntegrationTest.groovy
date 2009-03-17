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
class WebProjectSampleIntegrationTest {
    static final String WEB_PROJECT_NAME = 'web-project'

    // Injected by test runner
    private GradleDistribution dist;

    @Test
    public void webProjectSamples() {
        String gradleHome = dist.gradleHomeDir.absolutePath
        File webProjectDir = new File(dist.samplesDir, WEB_PROJECT_NAME)
        Executer.execute(gradleHome, webProjectDir.absolutePath, ['clean', 'libs'], [], '', Executer.DEBUG)
        String unjarPath = "$webProjectDir/build/unjar"
        AntBuilder ant = new AntBuilder()
        ant.unjar(src: "$webProjectDir/build/$WEB_PROJECT_NAME-1.0.war", dest: unjarPath)
        ['root.txt', 'WEB-INF/classes/org/MyClass.class', 'WEB-INF/lib/compile-1.0.jar', 'WEB-INF/lib/runtime-1.0.jar',
                'WEB-INF/lib/additional-1.0.jar', 'WEB-INF/lib/otherConf-1.0.jar', 'WEB-INF/additional.xml', 'WEB-INF/webapp.xml', 'WEB-INF/web.xml',
                'webapp.html'].each {
            assert new File("$unjarPath/$it").isFile()
        }

        checkJettyPlugin(gradleHome, webProjectDir)

        Executer.execute(gradleHome, webProjectDir.absolutePath, ['clean'], [], '', Executer.DEBUG)
    }

    static void checkJettyPlugin(String gradleHome, File webProjectDir) {
        Executer.execute(gradleHome, webProjectDir.absolutePath, ['clean', 'runTest'], [], '', Executer.DEBUG)
        checkServletOutput(webProjectDir)
        Executer.execute(gradleHome, webProjectDir.absolutePath, ['clean', 'runWarTest'], [], '', Executer.DEBUG)
        checkServletOutput(webProjectDir)
        Executer.execute(gradleHome, webProjectDir.absolutePath, ['clean', 'runExplodedTest'], [], '', Executer.DEBUG)
        checkServletOutput(webProjectDir)
    }

    static void checkServletOutput(File webProjectDir) {
        Assert.assertEquals('Hello Gradle', new File(webProjectDir, "build/servlet-out.txt").text)
    }
}
