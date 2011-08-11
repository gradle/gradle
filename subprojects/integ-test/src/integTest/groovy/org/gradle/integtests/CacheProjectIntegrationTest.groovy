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
    TestFile dependenciesCache

    @Before
    public void setUp() {
        String version = GradleVersion.current().version
        projectDir = dist.getTestDir().file("project")
        projectDir.mkdirs()
        userHomeDir = dist.getUserHomeDir()
        buildFile = projectDir.file('build.gradle')
        ScriptSource source = new UriScriptSource("build file", buildFile)
        propertiesFile = userHomeDir.file("caches/$version/scripts/$source.className/ProjectScript/no_buildscript/cache.properties")
        classFile = userHomeDir.file("caches/$version/scripts/$source.className/ProjectScript/no_buildscript/classes/${source.className}.class")
        dependenciesCache = userHomeDir.file("caches/artifacts/commons-io/commons-io/")
        artifactsCache = projectDir.file(".gradle/$version/taskArtifacts/cache.bin")
    }

    @Test
    public void "caches compiled build script"() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        TestFile.Snapshot classFileSnapshot = classFile.snapshot()

        testBuild("hello2", "Hello 2")
        classFile.assertHasNotChangedSince(classFileSnapshot)

        modifyLargeBuildScript()
        testBuild("newTask", "I am new")
        classFile.assertHasChangedSince(classFileSnapshot)
        classFileSnapshot = classFile.snapshot()

        testBuild("newTask", "I am new", "-Crebuild")
        classFile.assertHasChangedSince(classFileSnapshot)
    }

    @Test
    public void "caches incremental build state"() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        TestFile.Snapshot artifactsCacheSnapshot = artifactsCache.snapshot()

        testBuild("hello1", "Hello 1")
        artifactsCache.assertHasNotChangedSince(artifactsCacheSnapshot)

        testBuild("hello2", "Hello 2")
        artifactsCache.assertHasChangedSince(artifactsCacheSnapshot)
        artifactsCacheSnapshot = artifactsCache.snapshot()

        testBuild("hello2", "Hello 2", "-Crebuild")
        artifactsCache.assertHasChangedSince(artifactsCacheSnapshot)
    }

    @Test
    public void "does not rebuild artifact cache when run with --cache rebuild"() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        assert dependenciesCache.isDirectory() && dependenciesCache.listFiles().length > 0

        modifyLargeBuildScript()
        testBuild("newTask", "I am new", "-Crebuild")
        assert dependenciesCache.isDirectory() && dependenciesCache.listFiles().length > 0
    }

    private def testBuild(String taskName, String expected, String... args) {
        executer.inDirectory(projectDir).withTasks(taskName).withArguments(args).run()
        assertEquals(expected, projectDir.file(TEST_FILE).text)
        classFile.assertIsFile()
        propertiesFile.assertIsFile()
        artifactsCache.assertIsFile()
    }

    // We once ran into a cache problem under windows, which was not reproducible with small build scripts. Therefore we
    // create a larger one here.

    def createLargeBuildScript() {
        File buildFile = projectDir.file('build.gradle')
        String content = """
repositories { mavenCentral() }
configurations { compile }
dependencies { compile 'commons-io:commons-io:1.4@jar' }
"""

        50.times {i ->
            content += """
task 'hello$i' {
    File file = file('$TEST_FILE')
    outputs.file file
    doLast {
        configurations.compile.resolve()
        file.parentFile.mkdirs()
        file.write('Hello $i')
    }
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
configurations { other }
dependencies { other 'commons-lang:commons-lang:2.6@jar' }

task newTask {
    File file = file('$TEST_FILE')
    outputs.file file
    doLast {
        configurations.other.resolve()
        file.parentFile.mkdirs()
        file.write('I am new')
    }
}
"""
        buildFile.write(newContent)
    }
}
