/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.wrapper

import groovy.mock.interceptor.MockFor
import org.gradle.Main
import org.gradle.MainTest
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.util.HelperUtil
import org.gradle.util.TestConsts
import org.gradle.wrapper.Install

/**
 * @author Hans Dockter
 */
class WrapperTest extends AbstractConventionTaskTest {
    Wrapper wrapper
    MockFor wrapperScriptGeneratorMocker
    File testDir
    File gradleWrapperHome
    File sourceWrapperJar
    File targetWrapperJar

    void setUp() {
        super.setUp()
        wrapper = new Wrapper(project, AbstractTaskTest.TEST_TASK_NAME)
        wrapperScriptGeneratorMocker = new MockFor(WrapperScriptGenerator)
        wrapper.scriptDestinationDir = new File('scriptDestination')
        wrapper.gradleVersion = '1.0'
        testDir = HelperUtil.makeNewTestDir()
        File gradleHomeLib = new File(testDir.absolutePath + '/gradleHome', 'lib')
        gradleHomeLib.mkdirs()
        sourceWrapperJar = new File(gradleHomeLib.absolutePath, "$Install.WRAPPER_DIR-${TestConsts.VERSION}.jar")
        sourceWrapperJar.write('sometext' + System.currentTimeMillis())
        System.properties[Main.GRADLE_HOME] = gradleHomeLib.parent
        gradleWrapperHome = new File(testDir, Install.WRAPPER_DIR)
        targetWrapperJar = new File(gradleWrapperHome, Install.WRAPPER_JAR)
    }

    void tearDown() {
        System.properties[Main.GRADLE_HOME] = MainTest.TEST_GRADLE_HOME
        HelperUtil.deleteTestDir()
    }

    Task getTask() {
        wrapper
    }

    void testWrapper() {
        wrapper = new Wrapper(project, AbstractTaskTest.TEST_TASK_NAME)
        assert wrapper.wrapperScriptGenerator
        assertEquals(project.projectDir, wrapper.gradleWrapperHomeParent)
        assertEquals(project.projectDir, wrapper.scriptDestinationDir)
        assertEquals(Wrapper.DEFAULT_URL_ROOT, wrapper.urlRoot)
    }

    void testExecuteWithNonExistingWrapperHome() {
        checkExecute()
    }

    void testExecuteWithExistingWrapperHome() {
        gradleWrapperHome.mkdirs()
        targetWrapperJar.createNewFile()
        checkExecute()
    }

    private void checkExecute() {
        wrapper.gradleWrapperHomeParent = testDir
        wrapperScriptGeneratorMocker.demand.generate(1..1) {String gradleVersion, String downloadUrlRoot,
                                                            File scriptDestinationDir, AntBuilder ant ->
            assertEquals(wrapper.gradleVersion, gradleVersion)
            assertEquals(wrapper.urlRoot, downloadUrlRoot)
            assertEquals(wrapper.scriptDestinationDir, scriptDestinationDir)
            assert ant.is(project.ant)
        }
        wrapperScriptGeneratorMocker.use(wrapper.wrapperScriptGenerator) {
            wrapper.execute()
        }
        assertEquals(sourceWrapperJar.text, targetWrapperJar.text)
    }
}