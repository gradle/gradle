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

package org.gradle.integtests

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.UriScriptSource
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.GradleVersion
import org.gradle.util.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class CacheProjectIntegrationTest {
    static final String TEST_FILE = "build/test.txt"

    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    TestFile projectDir
    TestFile userHomeDir
    TestFile buildFile
    TestFile propertiesFile
    TestFile classFile
    TestFile artifactsCache

    @Before
    public void setUp() {
        String version = GradleVersion.current().version
        projectDir = dist.getTestDir().file("project")
        projectDir.mkdirs()
        userHomeDir = dist.getUserHomeDir()
        buildFile = projectDir.file('build.gradle')
        ScriptSource source = new UriScriptSource("build file", buildFile)
        propertiesFile = userHomeDir.file("caches/$version/scripts/$source.className/cache.properties")
        classFile = userHomeDir.file("caches/$version/scripts/$source.className/no_buildscript_ProjectScript/${source.className}.class")
        artifactsCache = projectDir.file(".gradle/$version/taskArtifacts/cache.bin")
    }

    @Test
    public void cachesBuildScript() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        TestFile.Snapshot classFileSnapshot = classFile.snapshot()
        TestFile.Snapshot artifactsCacheSnapshot = artifactsCache.snapshot()

        testBuild("hello2", "Hello 2")
        classFile.assertHasNotChangedSince(classFileSnapshot)
        artifactsCache.assertHasNotChangedSince(artifactsCacheSnapshot)

        modifyLargeBuildScript()
        testBuild("newTask", "I am new")
        classFile.assertHasChangedSince(classFileSnapshot)
        artifactsCache.assertHasNotChangedSince(artifactsCacheSnapshot)
        classFileSnapshot = classFile.snapshot()
        artifactsCacheSnapshot = artifactsCache.snapshot()

        testBuild("newTask", "I am new", "-Crebuild")
        classFile.assertHasChangedSince(classFileSnapshot)
        artifactsCache.assertHasChangedSince(artifactsCacheSnapshot)
    }

    private def testBuild(String taskName, String expected, String... args) {
        executer.inDirectory(projectDir).withTasks(taskName).withArguments(args).withQuietLogging().run()
        assertEquals(expected, projectDir.file(TEST_FILE).text)
        classFile.assertIsFile()
        propertiesFile.assertIsFile()
        artifactsCache.assertIsFile()
    }

    // We once ran into a cache problem under windows, which was not reproducible with small build scripts. Therefore we
    // create a larger one here.

    def createLargeBuildScript() {
        File buildFile = projectDir.file('build.gradle')
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
        File buildFile = projectDir.file('build.gradle')
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
