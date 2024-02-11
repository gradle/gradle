/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.plugins.ide.AbstractIdeIntegrationTest

class AbstractEclipseIntegrationTest extends AbstractIdeIntegrationTest {
    protected ExecutionResult runEclipseTask(settingsScript = "rootProject.name = 'root'", buildScript) {
        runTask("eclipse", settingsScript, buildScript)
    }

    protected File getProjectFile(Map options) {
        getFile(options, ".project")
    }

    protected File getClasspathFile(Map options) {
        getFile(options, ".classpath")
    }

    protected File getComponentFile(Map options) {
        getFile(options, ".settings/org.eclipse.wst.common.component")
    }

    protected File getFacetFile(Map options) {
        getFile(options, ".settings/org.eclipse.wst.common.project.facet.core.xml")
    }

    protected File getJdtPropertiesFile(Map options) {
        getFile(options, ".settings/org.eclipse.jdt.core.prefs")
    }

    protected parseProjectFile(Map options) {
        parseFile(options, ".project")
    }

    protected parseClasspathFile(Map options) {
        parseFile(options, ".classpath")
    }

    protected parseComponentFile(Map options) {
        parseFile(options, ".settings/org.eclipse.wst.common.component")
    }

    protected parseFacetFile(Map options) {
        parseFile(options, ".settings/org.eclipse.wst.common.project.facet.core.xml")
    }

    protected String parseJdtFile() {
        return getFile([:], '.settings/org.eclipse.jdt.core.prefs').text
    }

    protected findEntries(classpath, kind) {
        classpath.classpathentry.findAll { it.@kind == kind }
    }

    protected libEntriesInClasspathFileHaveFilenames(String... filenames) {
        def classpath = parseClasspathFile()
        def libs = findEntries(classpath, "lib")
        assert libs*.@path*.text().collect { new File(it).name } as Set == filenames as Set
    }

    protected EclipseWtpComponentFixture getWtpComponent() {
        EclipseWtpComponentFixture.create(testDirectory)
    }

    protected EclipseWtpComponentFixture wtpComponent(String project) {
        EclipseWtpComponentFixture.create(testDirectory.file(project))
    }

    protected EclipseClasspathFixture getClasspath() {
        EclipseClasspathFixture.create(testDirectory, executer.gradleUserHomeDir)
    }

    protected EclipseClasspathFixture classpath(String path) {
        EclipseClasspathFixture.create(testDirectory.file(path), executer.gradleUserHomeDir)
    }

    protected EclipseWtpFacetsFixture getWtpFacets() {
        EclipseWtpFacetsFixture.create(testDirectory)
    }
}
