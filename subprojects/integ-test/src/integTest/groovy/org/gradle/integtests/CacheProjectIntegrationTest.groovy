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

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.util.GradleVersion
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Issue

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class CacheProjectIntegrationTest extends AbstractIntegrationTest {
    static final String TEST_FILE = "build/test.txt"

    @Rule
    public final HttpServer server = new HttpServer()

    TestFile projectDir
    TestFile userHomeDir
    TestFile buildFile
    TestFile propertiesFile
    TestFile classPathClassesDir
    TestFile classFile
    TestFile artifactsCache

    MavenHttpRepository repo

    @Before
    public void setUp() {
        // Use own home dir so we don't blast the shared one when we run with -C rebuild
        executer.requireOwnGradleUserHomeDir()

        String version = GradleVersion.current().version
        projectDir = file("project")
        projectDir.mkdirs()
        userHomeDir = executer.gradleUserHomeDir
        buildFile = projectDir.file('build.gradle')

        artifactsCache = projectDir.file(".gradle/$version/executionHistory/executionHistory.bin")

        repo = new MavenHttpRepository(server, mavenRepo)

        repo.module("commons-io", "commons-io", "1.4").publish().allowAll()
        repo.module("commons-lang", "commons-lang", "2.6").publish().allowAll()

        server.start()
    }

    private void updateCaches() {
        String version = GradleVersion.current().version
        classPathClassesDir = userHomeDir.file("caches/$version/scripts").listFiles().find { it.file("cp_proj").isDirectory() }?.file("cp_proj")
        def candidates = userHomeDir.file("caches/$version/scripts").listFiles().findAll { it.file("proj").isDirectory() }
        // when there are multiple candidates, assume that a different entry to that used last time is the one required
        def baseDir = candidates.size() == 1 ? candidates.first() : candidates.find { propertiesFile == null || it != propertiesFile.parentFile }
        propertiesFile = baseDir.file("cache.properties")
        classFile = baseDir.file("proj/_BuildScript_.class")
    }

    @Test
    void "caches compiled build script"() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        TestFile.Snapshot classFileSnapshot = classFile.snapshot()

        testBuild("hello2", "Hello 2")
        classFile.assertHasNotChangedSince(classFileSnapshot)

        modifyLargeBuildScript()
        testBuild("newTask", "I am new")
        classFile.assertHasChangedSince(classFileSnapshot)
    }

    @Issue("https://github.com/gradle/gradle/issues/13367")
    @Test
    void "recovers from discarded empty classes directory from classpath entry"() {
        buildFile << """
            task hello1 {
                def f = file("build/test.txt")
                outputs.file(f)
                doLast {
                    f.text = "Hello 1"
                }
            }
        """

        testBuild("hello1", "Hello 1")
        assertTrue(classPathClassesDir.isDirectory() && classPathClassesDir.list().length == 0)
        classPathClassesDir.deleteDir()

        testBuild("hello1", "Hello 1")
    }

    @Test
    void "caches incremental build state"() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")
        TestFile.Snapshot artifactsCacheSnapshot = artifactsCache.snapshot()

        testBuild("hello1", "Hello 1")
        artifactsCache.assertHasNotChangedSince(artifactsCacheSnapshot)

        testBuild("hello2", "Hello 2")
        artifactsCache.assertHasChangedSince(artifactsCacheSnapshot)
        artifactsCacheSnapshot = artifactsCache.snapshot()

        testBuild("hello2", "Hello 2", "-rerun-tasks")
        artifactsCache.assertHasChangedSince(artifactsCacheSnapshot)
    }

    @Test
    void "does not rebuild artifact cache when run with --rerun-tasks"() {
        createLargeBuildScript()
        testBuild("hello1", "Hello 1")

        TestFile dependenciesCache = findDependencyCacheDir()
        assert dependenciesCache.isDirectory() && dependenciesCache.listFiles().length > 0

        modifyLargeBuildScript()
        testBuild("newTask", "I am new", "--rerun-tasks")
        assert dependenciesCache.isDirectory() && dependenciesCache.listFiles().length > 0
    }

    private TestFile findDependencyCacheDir() {
        def resolverArtifactCache = new TestFile(userHomeDir.file("caches/${CacheLayout.ROOT.getKey()}/${CacheLayout.FILE_STORE.getKey()}"))
        return resolverArtifactCache.file("commons-io/commons-io/")
    }

    private def testBuild(String taskName, String expected, String... args) {
        executer.inDirectory(projectDir).withTasks(taskName).withArguments(args).run()
        assertEquals(expected, projectDir.file(TEST_FILE).text)
        updateCaches()
        classFile.assertIsFile()
        propertiesFile.assertIsFile()
        artifactsCache.assertIsFile()
    }

    // We once ran into a cache problem under windows, which was not reproducible with small build scripts. Therefore we
    // create a larger one here.

    def createLargeBuildScript() {
        File buildFile = projectDir.file('build.gradle')
        String content = """
repositories {
    maven{
        url "${repo.uri}"
    }
}
configurations { compile }
dependencies { compile 'commons-io:commons-io:1.4@jar' }
"""

        50.times { i ->
            content += """
task 'hello$i' {
    File file = file('$TEST_FILE')
    outputs.file file
    def compileFiles = provider {
        configurations.compile.resolve()
    }
    doLast {
        compileFiles.get()
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
    def otherFiles = provider {
        configurations.other.resolve()
    }
    doLast {
        otherFiles.get()
        file.parentFile.mkdirs()
        file.write('I am new')
    }
}
"""
        buildFile.write(newContent)
    }
}
