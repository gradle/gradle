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
import org.junit.Before
import org.gradle.groovy.scripts.FileScriptSource
import org.gradle.groovy.scripts.ScriptSource

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class CacheProjectIntegrationTest {
    static final String TEST_FILE = "build/test.txt"

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    TestFile projectDir
    TestFile userHomeDir
    TestFile buildFile
    TestFile propertiesFile
    TestFile classFile

    @Before
    public void setUp() {
        projectDir = dist.getTestDir().file("project")
        projectDir.mkdirs()
        userHomeDir = dist.getTestDir().file("user")
        buildFile = projectDir.file('build.gradle')
        ScriptSource source = new FileScriptSource("build file", buildFile)
        propertiesFile = userHomeDir.file("scriptCache/$source.className/BuildScriptTransformer/cache.properties")
        classFile = userHomeDir.file("scriptCache/$source.className/BuildScriptTransformer/${source.className}.class")
    }
    
    @Test
    public void cacheProject() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        long modTime = classFile.lastModified()

        testBuild("hello2", "Hello 2")
        assertThat(classFile.lastModified(), equalTo(modTime))

        changeCacheVersionProperty()
        testBuild("hello2", "Hello 2")
        assertThat(classFile.lastModified(), not(equalTo(modTime)))
        modTime = classFile.lastModified()

        modifyLargeBuildScript()
        testBuild("newTask", "I am new")
        assertThat(classFile.lastModified(), not(equalTo(modTime)))
    }

    private def changeCacheVersionProperty() {
        Properties properties = new Properties()
        FileInputStream propertiesInputStream = new FileInputStream(propertiesFile)
        properties.load(propertiesInputStream)
        propertiesInputStream.close()
        properties.put("version", "0.5.1")
        FileOutputStream propertiesOutputStream = new FileOutputStream(propertiesFile)
        properties.store(propertiesOutputStream, null)
        propertiesOutputStream.close()
    }

    private def testBuild(String taskName, String expected) {
        executer.inDirectory(projectDir).withArguments("--gradle-user-home", userHomeDir.getAbsolutePath()).withTasks(taskName).withQuietLogging().run()
        assertEquals(expected, projectDir.file(TEST_FILE).text)
        classFile.assertIsFile()
        propertiesFile.assertIsFile()
    }

    // We once ran into a cache problem under windows, which was not reproducible with small build scripts. Therefore we
    // create a larger one here.
    def createLargeBuildScript() {
        File buildFile = new File(projectDir, 'build.gradle')
        String content = ""
        50.times {i ->
            content += """task 'hello$i' << {
    File file = file('$TEST_FILE')
    file.parentFile.mkdirs()
    file.write('Hello $i')
}

void someMethod$i() {
    println('Some message')
}

"""
        }
        buildFile.write(content)
    }

    def void modifyLargeBuildScript() {
        File buildFile = new File(projectDir, 'build.gradle')
        String newContent = buildFile.text + """
task newTask << {
    File file = file('$TEST_FILE')
    file.parentFile.mkdirs()
    file.write('I am new')
}
"""
        buildFile.write(newContent) 
    }
}
