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

package org.gradle.integtests

import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.GFileUtils
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesJavaMultiProjectIntegrationTest {

    static final String JAVA_PROJECT_NAME = 'java/multiproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

    private TestFile javaprojectDir
    private List projects;

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Before
    void setUp() {
        javaprojectDir = dist.samplesDir.file('java/multiproject')
        projects = [SHARED_NAME, API_NAME, WEBAPP_PATH].collect {"$JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
        deleteBuildDir(projects)
    }

    @After
    void tearDown() {
        deleteBuildDir(projects)
    }

    private def deleteBuildDir(List projects) {
        return projects.each {GFileUtils.deleteDirectory(new File(dist.samplesDir, "$it/build"))}
    }

    @Test
    public void multiProjectjavaProjectSample() {
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('build').run()

        assertEverythingBuilt()
    }

    private void assertEverythingBuilt() {
        String packagePrefix = 'build/classes/main/org/gradle'
        String testPackagePrefix = 'build/classes/test/org/gradle'

        // Check classes and resources
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')

        // Check test classes and resources
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        assertExists(javaprojectDir, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
        assertExists(javaprojectDir, WEBAPP_PATH, packagePrefix, WEBAPP_NAME, 'TestTest.class')

        // Check test results and report
        assertExists(javaprojectDir, SHARED_NAME, 'build/test-results/TEST-org.gradle.shared.PersonTest.xml')
        assertExists(javaprojectDir, SHARED_NAME, 'build/test-results/TESTS-TestSuites.xml')
        assertExists(javaprojectDir, SHARED_NAME, 'build/reports/tests/index.html')
        assertExists(javaprojectDir, WEBAPP_PATH, 'build/test-results/TEST-org.gradle.webservice.TestTestTest.xml')
        assertExists(javaprojectDir, WEBAPP_PATH, 'build/test-results/TESTS-TestSuites.xml')
        assertExists(javaprojectDir, WEBAPP_PATH, 'build/reports/tests/index.html')

        // Check contents of shared jar
        TestFile tmpDir = dist.testDir.file("$SHARED_NAME-1.0.jar")
        javaprojectDir.file(SHARED_NAME, "build/libs/$SHARED_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/shared/Person.class',
                'org/gradle/shared/main.properties'
        )

        // Check contents of API jar
        tmpDir = dist.testDir.file("$API_NAME-1.0.jar")
        javaprojectDir.file(API_NAME, "build/libs/$API_NAME-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/api/PersonList.class',
                'org/gradle/apiImpl/Impl.class')

        // Check contents of API jar
        tmpDir = dist.testDir.file("$API_NAME-spi-1.0.jar")
        javaprojectDir.file(API_NAME, "build/libs/$API_NAME-spi-1.0.jar").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/api/PersonList.class')

        // Check contents of War
        tmpDir = dist.testDir.file("$WEBAPP_NAME-2.5.war")
        javaprojectDir.file(WEBAPP_PATH, "build/libs/$WEBAPP_NAME-2.5.war").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'WEB-INF/classes/org/gradle/webservice/TestTest.class',
                "WEB-INF/lib/$SHARED_NAME-1.0.jar".toString(),
                "WEB-INF/lib/$API_NAME-1.0.jar".toString(),
                "WEB-INF/lib/$API_NAME-spi-1.0.jar".toString(),
                'WEB-INF/lib/commons-collections-3.2.jar',
                'WEB-INF/lib/commons-io-1.2.jar',
                'WEB-INF/lib/commons-lang-2.4.jar'
        )

        // Check contents of dist zip
        tmpDir = dist.testDir.file("$API_NAME-1.0.zip")
        javaprojectDir.file(API_NAME, "build/distributions/$API_NAME-1.0.zip").unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
                'README.txt',
                "$API_NAME-spi-1.0.jar".toString(),
                "$SHARED_NAME-1.0.jar".toString(),
                'commons-io-1.2.jar',
                'commons-lang-2.4.jar'
        )
    }

    @Test
    public void multiProjectJavaDoc() {
        executer.inDirectory(javaprojectDir).withTasks('clean', 'javadoc').run()
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/index.html').assertIsFile()
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/Person.html').assertIsFile()
        javaprojectDir.file(SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/package-summary.html').assertContents(containsString("These are the shared classes."))
        javaprojectDir.file(API_NAME, 'build/docs/javadoc/index.html').assertIsFile()
        javaprojectDir.file(API_NAME, 'build/docs/javadoc/org/gradle/api/PersonList.html').assertIsFile()
        javaprojectDir.file(API_NAME, 'build/docs/javadoc/org/gradle/api/package-summary.html').assertContents(containsString("These are the API classes"))
        javaprojectDir.file(WEBAPP_PATH, 'build/docs/javadoc/index.html').assertIsFile()
    }

    @Test
    public void multiProjectPartialBuild() {
        String packagePrefix = 'build/classes/main/org/gradle'
        String testPackagePrefix = 'build/classes/test/org/gradle'

        // Partial build using current directory
        executer.inDirectory(javaprojectDir.file("$SERVICES_NAME/$WEBAPP_NAME")).withTasks(JavaPlugin.BUILD_NEEDED_TASK_NAME).run()
        checkPartialWebAppBuild(packagePrefix, javaprojectDir, testPackagePrefix)

        // Partial build using task path
        executer.inDirectory(javaprojectDir).withTasks('clean', "$SHARED_NAME:compile".toString()).run()
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertDoesNotExist(javaprojectDir, false, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
    }


    @Test
    public void buildDependents() {
        executer.inDirectory(javaprojectDir).withTasks('clean', "$SHARED_NAME:${JavaPlugin.BUILD_DEPENDENTS_TASK_NAME}".toString()).run()
        assertEverythingBuilt()
    }

    @Test
    public void clean() {
        executer.inDirectory(javaprojectDir).withTasks('compile').run()
        executer.inDirectory(javaprojectDir).withTasks('clean').run()
        projects.each {assert !(new File(dist.samplesDir, "$it/build").exists())}
    }

    @Test
    public void noRebuildOfProjectDependencies() {
        TestFile apiDir = javaprojectDir.file(API_NAME)
        executer.inDirectory(apiDir).withTasks('compile').run()
        TestFile sharedJar = javaprojectDir.file(".gradle/internal-repository/org.gradle/shared/1.0/jars/shared.jar")
        long oldTimeStamp = sharedJar.lastModified()
        executer.inDirectory(apiDir).withTasks('clean', 'compile').withArguments("-a").run()
        long newTimeStamp = sharedJar.lastModified()
        assertThat(newTimeStamp, Matchers.equalTo(oldTimeStamp))
    }

    @Test
    public void additionalProjectDependenciesTasks() {
        TestFile apiDir = javaprojectDir.file(API_NAME)
        executer.inDirectory(apiDir).withTasks('compile').withArguments("-A javadoc").run()
        assertExists(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/index.html')
    }
           
    private static def checkPartialWebAppBuild(String packagePrefix, TestFile javaprojectDir, String testPackagePrefix) {
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, packagePrefix, WEBAPP_NAME, 'TestTest.class')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'libs', 'webservice-2.5.war')
        assertDoesNotExist(javaprojectDir, false, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'libs', 'webservice-2.5.jar')
    }

    private static void assertExists(TestFile baseDir, Object... path) {
        baseDir.file(path).assertExists()
    }

    static void assertDoesNotExist(TestFile baseDir, boolean shouldExists, Object... path) {
        baseDir.file(path).assertDoesNotExist()
    }
}
