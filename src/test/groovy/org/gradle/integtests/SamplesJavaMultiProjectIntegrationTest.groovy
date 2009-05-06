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

import groovy.text.SimpleTemplateEngine
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import static org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Before
import org.junit.After
import static org.junit.Assert.*
import org.gradle.util.GFileUtils
import org.hamcrest.Matchers

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesJavaMultiProjectIntegrationTest {

    static final String JAVA_PROJECT_NAME = 'javaproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

    private File javaprojectDir
    private List projects;

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Before
    void setUp() {
        javaprojectDir = new File(dist.samplesDir, 'java/multiproject')
        projects = [SHARED_NAME, API_NAME, WEBAPP_NAME, SERVICES_NAME].collect {"JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
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
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'

        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('dists').run()

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

        // Check jar exists
        assertExists(javaprojectDir, SHARED_NAME, "build/$SHARED_NAME-1.0.jar".toString())
        assertExists(javaprojectDir, API_NAME, "build/$API_NAME-1.0.jar".toString())
        assertExists(javaprojectDir, API_NAME, "build/$API_NAME-spi-1.0.jar".toString())
        assertExists(javaprojectDir, WEBAPP_PATH, "build/$WEBAPP_NAME-2.5.war".toString())

        // Check dist zip exists
        assertExists(javaprojectDir, API_NAME, "build/distributions/$API_NAME-1.0.zip".toString())
    }

    @Test
    public void multiProjectJavaDoc() {
        executer.inDirectory(javaprojectDir).withTasks('javadoc').run()
        assertExists(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/index.html')
        assertTrue(fileText(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/package-summary.html').contains("These are the shared classes."))
        assertExists(javaprojectDir, API_NAME, 'build/docs/javadoc/index.html')
        assertTrue(fileText(javaprojectDir, API_NAME, 'build/docs/javadoc/org/gradle/api/package-summary.html').contains("These are the API classes"))
        assertExists(javaprojectDir, WEBAPP_PATH, 'build/docs/javadoc/index.html')
    }

    @Test
    public void multiProjectPartialBuild() {
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'

        // Partial build using current directory
        executer.inDirectory(new File(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME")).withTasks('libs').run()
        checkPartialWebAppBuild(packagePrefix, javaprojectDir, testPackagePrefix)

        // Partial build using task path
        executer.inDirectory(javaprojectDir).withTasks('clean', "$SHARED_NAME:compile".toString()).run()
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertDoesNotExist(javaprojectDir, false, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
    }

    @Test
    public void clean() {
        executer.inDirectory(javaprojectDir).withTasks('compile').run()
        executer.inDirectory(javaprojectDir).withTasks('clean').run()
        projects.each {assert !(new File(dist.samplesDir, "$it/build").exists())}
    }

    @Test
    public void noRebuildOfProjectDependencies() {
        File apiDir = new File(javaprojectDir, API_NAME)
        executer.inDirectory(apiDir).withTasks('compile').run()
        File sharedJar = new File(javaprojectDir, ".gradle/build-resolver/org.gradle/shared/1.0/jars/shared.jar")
        long oldTimeStamp = sharedJar.lastModified()
        executer.inDirectory(apiDir).withTasks('clean', 'compile').withArguments("-a").run()
        long newTimeStamp = sharedJar.lastModified()
        assertThat(newTimeStamp, Matchers.equalTo(oldTimeStamp))
    }

    @Test
    public void additionalProjectDependenciesTasks() {
        File apiDir = new File(javaprojectDir, API_NAME)
        executer.inDirectory(apiDir).withTasks('compile').withArguments("-A javadoc").run()
        assertExists(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/index.html')
    }
    
    @Test
    public void eclipseGeneration() {
        executer.inDirectory(javaprojectDir).withTasks('eclipse').run()

        String cachePath = System.properties['user.home'] + '/.gradle/cache'
        compareXmlWithIgnoringOrder(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream("javaproject/expectedApiProjectFile.txt").text,
                file(javaprojectDir, API_NAME, ".project").text)
        compareXmlWithIgnoringOrder(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream("javaproject/expectedWebApp1ProjectFile.txt").text,
                file(javaprojectDir, WEBAPP_PATH, ".project").text)
        compareXmlWithIgnoringOrder(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream("javaproject/expectedWebApp1ProjectFile.txt").text,
                file(javaprojectDir, WEBAPP_PATH, ".project").text)
        compareXmlWithIgnoringOrder(replaceWithCachePath("javaproject/expectedApiClasspathFile.txt", cachePath),
                file(javaprojectDir, API_NAME, ".classpath").text)
        compareXmlWithIgnoringOrder(replaceWithCachePath("javaproject/expectedWebApp1ClasspathFile.txt", cachePath),
                file(javaprojectDir, WEBAPP_PATH, ".classpath").text)
        compareXmlWithIgnoringOrder(replaceWithCachePath("javaproject/expectedWebApp1WtpFile.txt", cachePath),
                file(javaprojectDir, WEBAPP_PATH, ".settings/org.eclipse.wst.common.component").text)

        executer.inDirectory(javaprojectDir).withTasks('eclipseClean').run()
        assertDoesNotExist(javaprojectDir, false, API_NAME, ".project")
        assertDoesNotExist(javaprojectDir, false, WEBAPP_PATH, ".project")
        assertDoesNotExist(javaprojectDir, false, API_NAME, ".classpath")
        assertDoesNotExist(javaprojectDir, false, WEBAPP_PATH, ".project")
        assertDoesNotExist(javaprojectDir, false, WEBAPP_PATH, ".settings/org.eclipse.wst.common.component")
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        XMLAssert.assertXMLEqual(diff, true);
    }

    private static String replaceWithCachePath(String resourcePath, String cachePath) {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        templateEngine.createTemplate(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream(resourcePath).text).make(cachePath: new File(cachePath).canonicalPath).toString().replace('\\', '/')
    }
           
    private static def checkPartialWebAppBuild(String packagePrefix, File javaprojectDir, String testPackagePrefix) {
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        assertExists(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        assertExists(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, packagePrefix, WEBAPP_NAME, 'TestTest.class')
        assertExists(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'webservice-2.5.war')
        assertDoesNotExist(javaprojectDir, false, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'webservice-2.5.jar')
    }

    private static void assertExists(File baseDir, String[] path) {
        new TestFile(baseDir).file(path).assertExists()
    }

    static File file(File baseDir, String[] path) {
        new File(baseDir, path.join('/'));    
    }

    static void assertDoesNotExist(File baseDir, boolean shouldExists, String[] path) {
        new TestFile(baseDir).file(path).assertDoesNotExist()
    }

    static String fileText(File baseDir, String[] path) {
        file(baseDir, path).text
    }
}
