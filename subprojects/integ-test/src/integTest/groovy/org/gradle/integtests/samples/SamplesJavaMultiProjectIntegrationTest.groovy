/*
 * Copyright 2010 the original author or authors.
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



package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.containsString

@Ignore("contains buildSrc")
class SamplesJavaMultiProjectIntegrationTest extends AbstractIntegrationTest {

    static final String JAVA_PROJECT_NAME = 'java/multiproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

    private TestFile javaprojectDir
    private List projects;

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'java/multiproject')

    @Before
    void setUp() {
        javaprojectDir = sample.dir
        projects = [SHARED_NAME, API_NAME, WEBAPP_PATH].collect {"$JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
        executer.beforeExecute {
            executer.usingInitScript(RepoScriptBlockUtil.createMirrorInitScript())
        }
    }

    @Test
    void multiProjectJavaProjectSample() {
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('build').run()

        assertBuildSrcBuilt()
        assertEverythingBuilt()
    }

    private void assertBuildSrcBuilt() {
        TestFile buildSrcDir = javaprojectDir.file('buildSrc')

        buildSrcDir.file('build/libs/buildSrc.jar').assertIsFile()
        def result = new DefaultTestExecutionResult(buildSrcDir)
        result.assertTestClassesExecuted('org.gradle.buildsrc.BuildSrcClassTest')
    }

    private void assertEverythingBuilt() {
        String packagePrefix = 'build/classes/java/main/org/gradle'
        String testPackagePrefix = 'build/classes/java/test/org/gradle'
        String resPackagePrefix = 'build/resources/main/org/gradle'
        String testResPackagePrefix = 'build/resources/test/org/gradle'

        // Check classes and resources
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(javaprojectDir, SHARED_NAME, resPackagePrefix, SHARED_NAME, 'main.properties')

        // Check test classes and resources
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(javaprojectDir, SHARED_NAME, testResPackagePrefix, SHARED_NAME, 'test.properties')
        assertExists(javaprojectDir, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
        assertExists(javaprojectDir, WEBAPP_PATH, packagePrefix, WEBAPP_NAME, 'TestTest.class')

        // Check test results and report
        def result = new DefaultTestExecutionResult(javaprojectDir.file(SHARED_NAME))
        result.assertTestClassesExecuted('org.gradle.shared.PersonTest')

        result = new DefaultTestExecutionResult(javaprojectDir.file(WEBAPP_PATH))
        result.assertTestClassesExecuted('org.gradle.webservice.TestTestTest')

        // Check contents of shared jar
        TestFile tmpDir = file("$SHARED_NAME-1.0.jar")
        javaprojectDir.file(SHARED_NAME, "build/libs/$SHARED_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/shared/Person.class',
                // package-info.java only gets compiled into class if it contains at least one annotation
                // 'org/gradle/shared/package-info.class',
                'org/gradle/shared/main.properties'
        )

        // Check contents of API jar
        tmpDir = file("$API_NAME-1.0.jar")
        javaprojectDir.file(API_NAME, "build/libs/$API_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/api/PersonList.class',
                'org/gradle/apiImpl/Impl.class')

        // Check contents of API jar
        tmpDir = file("$API_NAME-spi-1.0.jar")
        javaprojectDir.file(API_NAME, "build/libs/$API_NAME-spi-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/api/PersonList.class')

        // Check contents of War
        tmpDir = file("$WEBAPP_NAME-2.5.war")
        javaprojectDir.file(WEBAPP_PATH, "build/libs/$WEBAPP_NAME-2.5.war").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'WEB-INF/classes/org/gradle/webservice/TestTest.class',
                "WEB-INF/lib/$SHARED_NAME-1.0.jar".toString(),
                "WEB-INF/lib/$API_NAME-1.0.jar".toString(),
                "WEB-INF/lib/$API_NAME-spi-1.0.jar".toString(),
                'WEB-INF/lib/commons-collections-3.2.2.jar',
                'WEB-INF/lib/commons-io-2.6.jar',
                'WEB-INF/lib/commons-lang3-3.7.jar'
        )

        // Check contents of dist zip
        tmpDir = file("$API_NAME-1.0.zip")
        javaprojectDir.file(API_NAME, "build/distributions/$API_NAME-1.0.zip").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'README.txt',
                "libs/$API_NAME-spi-1.0.jar".toString(),
                "libs/$SHARED_NAME-1.0.jar".toString(),
                'libs/commons-io-2.6.jar',
                'libs/commons-lang3-3.7.jar'
        )
    }

    @Test
    void multiProjectJavaDoc() {
        executer.inDirectory(javaprojectDir).withTasks('clean', 'javadoc').run()
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/index.html').assertIsFile()
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/index.html').assertContents(containsString("shared 1.0 API"))
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/Person.html').assertIsFile()
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/package-summary.html').assertContents(containsString("These are the shared classes."))
        javaprojectDir.file(API_NAME, 'build/docs/javadoc/index.html').assertIsFile()
        javaprojectDir.file(API_NAME, 'build/docs/javadoc/org/gradle/api/PersonList.html').assertIsFile()
        javaprojectDir.file(API_NAME, 'build/docs/javadoc/org/gradle/api/package-summary.html').assertContents(containsString("These are the API classes"))
        javaprojectDir.file(WEBAPP_PATH, 'build/docs/javadoc/index.html').assertIsFile()
    }

    @Test
    void multiProjectPartialBuild() {
        String packagePrefix = 'build/classes/java/main/org/gradle'
        String testPackagePrefix = 'build/classes/java/test/org/gradle'

        // Partial build using current directory
        executer.inDirectory(javaprojectDir.file("$SERVICES_NAME/$WEBAPP_NAME")).withTasks('buildNeeded').run()
        checkPartialWebAppBuild(packagePrefix, javaprojectDir, testPackagePrefix)

        // check resources
        assertExists(javaprojectDir, SHARED_NAME, 'build/resources/main/org/gradle', SHARED_NAME, 'main.properties')
        assertExists(javaprojectDir, SHARED_NAME, 'build/resources/test/org/gradle', SHARED_NAME, 'test.properties')

        // Partial build using task path
        executer.inDirectory(javaprojectDir).withTasks('clean', "$SHARED_NAME:classes".toString()).run()
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertDoesNotExist(javaprojectDir, false, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
    }


    @Test
    void buildDependents() {
        executer.inDirectory(javaprojectDir).withTasks('clean', "$SHARED_NAME:buildDependents".toString()).run()
        assertEverythingBuilt()
    }

    @Test
    void clean() {
        executer.inDirectory(javaprojectDir).withTasks('classes').run()
        executer.inDirectory(javaprojectDir).withTasks('clean').run()
        projects.each { javaprojectDir.file("$it/build").assertDoesNotExist() }
    }

    @Test
    void noRebuildOfProjectDependencies() {
        TestFile apiDir = javaprojectDir.file(API_NAME)
        executer.inDirectory(apiDir).withTasks('classes').run()
        TestFile sharedJar = javaprojectDir.file("shared/build/libs/shared-1.0.jar")
        TestFile.Snapshot snapshot = sharedJar.snapshot()
        executer.expectDeprecationWarning()
        executer.inDirectory(apiDir).withTasks('clean', 'classes').withArguments("-a").run()
        sharedJar.assertHasNotChangedSince(snapshot)
    }

    @Test
    void shouldNotUseCacheForProjectDependencies() {
        TestFile apiDir = javaprojectDir.file(API_NAME)
        executer.inDirectory(apiDir).withTasks('checkProjectDependency').run()
    }

    private static def checkPartialWebAppBuild(String packagePrefix, TestFile javaprojectDir, String testPackagePrefix) {
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, packagePrefix, WEBAPP_NAME, 'TestTest.class')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'libs', 'webservice-2.5.war')
    }

    private static void assertExists(TestFile baseDir, Object... path) {
        baseDir.file(path).assertExists()
    }

    static void assertDoesNotExist(TestFile baseDir, boolean shouldExists, Object... path) {
        baseDir.file(path).assertDoesNotExist()
    }
}
