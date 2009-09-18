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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesEclipseIntegrationTest {

    static final String ECLIPSE_PROJECT_NAME = 'eclipse'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'

    private TestFile eclipseProjectDir

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Before
    void setUp() {
        eclipseProjectDir = dist.samplesDir.file(ECLIPSE_PROJECT_NAME)
    }

    @Test
    public void eclipseGeneration() {
        executer.inDirectory(eclipseProjectDir).withTasks('clean', 'eclipse').run()

        String cachePath = dist.userHomeDir.file('cache')
        String resourcesRoot = 'eclipseproject'
        compareXmlWithIgnoringOrder(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream("$resourcesRoot/expectedApiProjectFile.txt").text,
                eclipseProjectDir.file(API_NAME, ".project").text)
        compareXmlWithIgnoringOrder(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream("$resourcesRoot/expectedWebserviceProjectFile.txt").text,
                eclipseProjectDir.file(WEBAPP_NAME, ".project").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedApiClasspathFile.txt", cachePath, "$eclipseProjectDir/$API_NAME" as File),
                eclipseProjectDir.file(API_NAME, ".classpath").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedWebserviceClasspathFile.txt", cachePath, "$eclipseProjectDir/$WEBAPP_NAME" as File),
                eclipseProjectDir.file(WEBAPP_NAME, ".classpath").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedWebserviceWtpFile.txt", cachePath, "$eclipseProjectDir/$WEBAPP_NAME" as File),
                eclipseProjectDir.file(WEBAPP_NAME, ".settings/org.eclipse.wst.common.component").text)

        executer.inDirectory(eclipseProjectDir).withTasks('eclipseClean').run()
        assertDoesNotExist(eclipseProjectDir, false, API_NAME, ".project")
        assertDoesNotExist(eclipseProjectDir, false, WEBAPP_NAME, ".project")
        assertDoesNotExist(eclipseProjectDir, false, API_NAME, ".classpath")
        assertDoesNotExist(eclipseProjectDir, false, WEBAPP_NAME, ".project")
        assertDoesNotExist(eclipseProjectDir, false, WEBAPP_NAME, ".settings/org.eclipse.wst.common.component")
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        XMLAssert.assertXMLEqual(diff, true);
    }

    private static String replacePaths(String resourcePath, String cachePath, File eclipseProjectDir) {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        templateEngine.createTemplate(SamplesJavaMultiProjectIntegrationTest.getResourceAsStream(resourcePath).text).make(
                cachePath: new File(cachePath).canonicalPath, projectDir: eclipseProjectDir.canonicalPath).toString().replace('\\', '/')
    }

    static void assertDoesNotExist(TestFile baseDir, boolean shouldExists, Object... path) {
        baseDir.file(path).assertDoesNotExist()
    }
}
