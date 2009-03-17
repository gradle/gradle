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
class CacheProject {
    static final String TEST_FILE = "build/test.txt"

    // Injected by test runner
    private GradleDistribution dist;

    @Test
    public void cacheProject() {
        File cacheProjectDir = new File(dist.samplesDir, "cache-project")
        cacheProjectDir.mkdirs();
        createLargeBuildScript(cacheProjectDir)
        testBuild(cacheProjectDir, dist.gradleHomeDir, "hello1", String.format("Hello 1"))
        changeCacheVersionProperty(cacheProjectDir)
        testBuild(cacheProjectDir, dist.gradleHomeDir, "hello2", String.format("Hello 2"))
        modifyLargeBuildScript(cacheProjectDir)
        testBuild(cacheProjectDir, dist.gradleHomeDir, "newTask", String.format("I am new"))
    }

    private static def changeCacheVersionProperty(File cacheProjectDir) {
        Properties properties = new Properties()
        FileInputStream propertiesInputStream = new FileInputStream(new File(cacheProjectDir, ".gradle/cache/build.gradle/cache.properties"))
        properties.load(propertiesInputStream)
        propertiesInputStream.close()
        properties.put("version", "0.5.1")
        FileOutputStream propertiesOutputStream = new FileOutputStream(new File(cacheProjectDir, ".gradle/cache/build.gradle/cache.properties"))
        properties.store(propertiesOutputStream, null)
        propertiesOutputStream.close()
    }

    private static def testBuild(File cacheProjectDir, File gradleHome, String taskName, String expected) {
        Executer.execute(gradleHome.absolutePath, cacheProjectDir.absolutePath, [taskName], [], '', Executer.QUIET)
        Assert.assertEquals(expected, new File(cacheProjectDir, TEST_FILE).text)
    }

    // We once ran into a cache problem under windows, which was not reproducible with small build scripts. Therefore we
    // create a larger one here.
    static createLargeBuildScript(File parentDir) {
        File buildFile = new File(parentDir, 'build.gradle')
        String content = ""
        50.times {i ->
            content += """createTask('hello$i') {
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

    static void modifyLargeBuildScript(File parentDir) {
        File buildFile = new File(parentDir, 'build.gradle')
        String newContent = buildFile.text + """
createTask('newTask') {
    File file = file('$TEST_FILE')
    file.parentFile.mkdirs()
    file.write('I am new')
}
"""
        buildFile.write(newContent) 
    }
}
