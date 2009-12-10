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

import groovy.text.SimpleTemplateEngine
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.ElementNameAndAttributeQualifier
import org.custommonkey.xmlunit.XMLAssert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.gradle.util.Resources

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class SamplesEclipseIntegrationTest {

    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'

    private TestFile eclipseProjectDir

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Rule public Resources resources = new Resources();

    TestFile cacheDir

    @Before
    void setUp() {
        cacheDir = dist.userHomeDir.file('cache')
    }

    @Test
    public void eclipseGenerationForJavaAndWebProjects() {
        TestFile eclipseProjectDir = dist.samplesDir.file('eclipse')
        executer.inDirectory(eclipseProjectDir).withTasks('clean', 'eclipse').run()

        String resourcesRoot = 'eclipseproject/java'
        compareXmlWithIgnoringOrder(resources.getResource("$resourcesRoot/expectedApiProjectFile.txt").text,
                eclipseProjectDir.file(API_NAME, ".project").text)
        compareXmlWithIgnoringOrder(resources.getResource("$resourcesRoot/expectedWebserviceProjectFile.txt").text,
                eclipseProjectDir.file(WEBAPP_NAME, ".project").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedApiClasspathFile.txt", cacheDir, "$eclipseProjectDir/$API_NAME" as File),
                eclipseProjectDir.file(API_NAME, ".classpath").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedWebserviceClasspathFile.txt", cacheDir, "$eclipseProjectDir/$WEBAPP_NAME" as File),
                eclipseProjectDir.file(WEBAPP_NAME, ".classpath").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedWebserviceWtpFile.txt", cacheDir, "$eclipseProjectDir/$WEBAPP_NAME" as File),
                eclipseProjectDir.file(WEBAPP_NAME, ".settings/org.eclipse.wst.common.component").text)

        executer.inDirectory(eclipseProjectDir).withTasks('eclipseClean').run()
        assertDoesNotExist(eclipseProjectDir, false, API_NAME, ".project")
        assertDoesNotExist(eclipseProjectDir, false, WEBAPP_NAME, ".project")
        assertDoesNotExist(eclipseProjectDir, false, API_NAME, ".classpath")
        assertDoesNotExist(eclipseProjectDir, false, WEBAPP_NAME, ".project")
        assertDoesNotExist(eclipseProjectDir, false, WEBAPP_NAME, ".settings/org.eclipse.wst.common.component")
    }

    @Test
    public void eclipseGenerationForGroovyProjects() {
        TestFile eclipseProjectDir = dist.samplesDir.file('groovy/quickstart')
        executer.inDirectory(eclipseProjectDir).withTasks('clean', 'eclipse').run()

        String resourcesRoot = 'eclipseproject/groovy'
        compareXmlWithIgnoringOrder(resources.getResource("$resourcesRoot/expectedProjectFile.txt").text,
                eclipseProjectDir.file(".project").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedClasspathFile.txt", cacheDir, eclipseProjectDir),
                eclipseProjectDir.file(".classpath").text)
    }

    @Test
    public void eclipseGenerationForScalaProjects() {
        TestFile eclipseProjectDir = dist.samplesDir.file('scala/quickstart')
        executer.inDirectory(eclipseProjectDir).withTasks('clean', 'eclipse').run()

        String resourcesRoot = 'eclipseproject/scala'
        compareXmlWithIgnoringOrder(resources.getResource("$resourcesRoot/expectedProjectFile.txt").text,
                eclipseProjectDir.file(".project").text)
        compareXmlWithIgnoringOrder(replacePaths("$resourcesRoot/expectedClasspathFile.txt", cacheDir, eclipseProjectDir),
                eclipseProjectDir.file(".classpath").text)
    }

    private static void compareXmlWithIgnoringOrder(String expectedXml, String actualXml) {
        Diff diff = new Diff(expectedXml, actualXml)
        diff.overrideElementQualifier(new ElementNameAndAttributeQualifier())
        XMLAssert.assertXMLEqual(diff, true);
    }

    private String replacePaths(String resourcePath, File cachePath, File eclipseProjectDir) {
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
        templateEngine.createTemplate(resources.getResource(resourcePath).text).make(
                cachePath: cachePath.canonicalPath, projectDir: eclipseProjectDir.canonicalPath).toString().replace('\\', '/')
    }

    static void assertDoesNotExist(TestFile baseDir, boolean shouldExists, Object ... path) {
        baseDir.file(path).assertDoesNotExist()
    }
}
