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

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class JavaProjectSampleIntegrationTest {
    static final String JAVA_PROJECT_NAME = 'javaproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void multiProjectjavaProjectSample() {
        List projects = [SHARED_NAME, API_NAME, WEBAPP_NAME, SERVICES_NAME].collect {"JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'
        File javaprojectDir = new File(dist.samplesDir, 'java/multiproject')

        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('clean', 'dists').run()

        // Check classes and resources
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')

        // Check test classes and resources
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        checkExistence(javaprojectDir, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
        checkExistence(javaprojectDir, WEBAPP_PATH, packagePrefix, WEBAPP_NAME, 'TestTest.class')

        // Check test results and report
        checkExistence(javaprojectDir, SHARED_NAME, 'build/test-results/TEST-org.gradle.shared.PersonTest.xml')
        checkExistence(javaprojectDir, SHARED_NAME, 'build/test-results/TESTS-TestSuites.xml')
        checkExistence(javaprojectDir, SHARED_NAME, 'build/reports/tests/index.html')
        checkExistence(javaprojectDir, WEBAPP_PATH, 'build/test-results/TEST-org.gradle.webservice.TestTestTest.xml')
        checkExistence(javaprojectDir, WEBAPP_PATH, 'build/test-results/TESTS-TestSuites.xml')
        checkExistence(javaprojectDir, WEBAPP_PATH, 'build/reports/tests/index.html')

        // Check jar exists
        checkExistence(javaprojectDir, SHARED_NAME, "build/$SHARED_NAME-1.0.jar".toString())
        checkExistence(javaprojectDir, API_NAME, "build/$API_NAME-1.0.jar".toString())
        checkExistence(javaprojectDir, API_NAME, "build/$API_NAME-spi-1.0.jar".toString())
        checkExistence(javaprojectDir, WEBAPP_PATH, "build/$WEBAPP_NAME-2.5.war".toString())

        // Check dist zip exists
        checkExistence(javaprojectDir, API_NAME, "build/distributions/$API_NAME-1.0.zip".toString())

        // Javdoc build
        executer.inDirectory(javaprojectDir).withTasks('clean', 'javadoc').run()
        checkExistence(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/index.html')
        assertTrue(fileText(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/package-summary.html').contains("These are the shared classes."))
        checkExistence(javaprojectDir, API_NAME, 'build/docs/javadoc/index.html')
        assertTrue(fileText(javaprojectDir, API_NAME, 'build/docs/javadoc/org/gradle/api/package-summary.html').contains("These are the API classes"))
        checkExistence(javaprojectDir, WEBAPP_PATH, 'build/docs/javadoc/index.html')

        // Partial build using current directory
        executer.inDirectory(new File(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME")).withTasks('clean', 'libs').run()
        checkPartialWebAppBuild(packagePrefix, javaprojectDir, testPackagePrefix)

        // Partial build using task path
        executer.inDirectory(javaprojectDir).withTasks('clean', "$SHARED_NAME:compile".toString()).run()
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        checkExistence(javaprojectDir, false, API_NAME, packagePrefix, API_NAME, 'PersonList.class')

        // This test is also important for test cleanup
        executer.inDirectory(javaprojectDir).withTasks('clean').run()
        projects.each {assert !(new File(dist.samplesDir, "$it/build").exists())}
    }

    @Test
    public void quickstartJavaProject() {
        File javaprojectDir = new File(dist.samplesDir, 'java/quickstart')

        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('clean', 'dists').run()

        // Check tests have run
        checkExistence(javaprojectDir, 'build/test-results/TEST-org.gradle.PersonTest.xml')
        checkExistence(javaprojectDir, 'build/test-results/TESTS-TestSuites.xml')

        // Check jar exists
        checkExistence(javaprojectDir, "build/quickstart-1.0.jar")
    }
    
    @Test
    public void javaProjectEclipseGeneration() {
        File javaprojectDir = new File(dist.samplesDir, 'java/multiproject')
        executer.inDirectory(javaprojectDir).withTasks('eclipse').run()

        String cachePath = System.properties['user.home'] + '/.gradle/cache'
        compareXmlWithIgnoringOrder(JavaProjectSampleIntegrationTest.getResourceAsStream("javaproject/expectedApiProjectFile.txt").text,
                file(javaprojectDir, API_NAME, ".project").text)
        compareXmlWithIgnoringOrder(JavaProjectSampleIntegrationTest.getResourceAsStream("javaproject/expectedWebApp1ProjectFile.txt").text,
                file(javaprojectDir, WEBAPP_PATH, ".project").text)
        compareXmlWithIgnoringOrder(JavaProjectSampleIntegrationTest.getResourceAsStream("javaproject/expectedWebApp1ProjectFile.txt").text,
                file(javaprojectDir, WEBAPP_PATH, ".project").text)
        compareXmlWithIgnoringOrder(replaceWithCachePath("javaproject/expectedApiClasspathFile.txt", cachePath),
                file(javaprojectDir, API_NAME, ".classpath").text)
        compareXmlWithIgnoringOrder(replaceWithCachePath("javaproject/expectedWebApp1ClasspathFile.txt", cachePath),
                file(javaprojectDir, WEBAPP_PATH, ".classpath").text)
        compareXmlWithIgnoringOrder(replaceWithCachePath("javaproject/expectedWebApp1WtpFile.txt", cachePath),
                file(javaprojectDir, WEBAPP_PATH, ".settings/org.eclipse.wst.common.component").text)

        executer.inDirectory(javaprojectDir).withTasks('eclipseClean').run()
        checkExistence(javaprojectDir, false, API_NAME, ".project")
        checkExistence(javaprojectDir, false, WEBAPP_PATH, ".project")
        checkExistence(javaprojectDir, false, API_NAME, ".classpath")
        checkExistence(javaprojectDir, false, WEBAPP_PATH, ".project")
        checkExistence(javaprojectDir, false, WEBAPP_PATH, ".settings/org.eclipse.wst.common.component")
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        XMLAssert.assertXMLEqual(diff, true);
    }

    private static String replaceWithCachePath(String resourcePath, String cachePath) {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        templateEngine.createTemplate(JavaProjectSampleIntegrationTest.getResourceAsStream(resourcePath).text).make(cachePath: new File(cachePath).canonicalPath).toString().replace('\\', '/')
    }
           
    private static def checkPartialWebAppBuild(String packagePrefix, File javaprojectDir, String testPackagePrefix) {
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        checkExistence(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, packagePrefix, WEBAPP_NAME, 'TestTest.class')
        checkExistence(javaprojectDir, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'webservice-2.5.war')
        checkExistence(javaprojectDir, false, "$SERVICES_NAME/$WEBAPP_NAME" as String, 'build', 'webservice-2.5.jar')
    }

    static void checkExistence(File baseDir, String[] path) {
        checkExistence(baseDir, true, path)
    }

    static File file(File baseDir, String[] path) {
        new File(baseDir, path.join('/'));    
    }

    static void checkExistence(File baseDir, boolean shouldExists, String[] path) {
        File file = file(baseDir, path)
        try {
            assert shouldExists ? file.exists() : !file.exists()
        } catch (AssertionError e) {
            if (shouldExists) {
                println("File: $file should exist, but does not!")
            } else {
                println("File: $file should not exist, but does!")
            }
            throw e
        }
    }

    static String fileText(File baseDir, String[] path) {
        new File(baseDir, path.join('/')).text
    }
}
