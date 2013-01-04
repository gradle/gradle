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

package org.gradle.api.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.internal.reflect.Instantiator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */
class JavaPluginConventionTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private DefaultProject project = HelperUtil.createRootProject()
    private Instantiator instantiator = project.services.get(Instantiator)
    private JavaPluginConvention convention

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Before public void setUp() {
        project.plugins.apply(ReportingBasePlugin)
        convention = new JavaPluginConvention(project, instantiator)
    }

    @Test public void defaultValues() {
        assertThat(convention.sourceSets, instanceOf(DefaultSourceSetContainer))
        assertThat(convention.manifest, notNullValue())
        assertEquals('dependency-cache', convention.dependencyCacheDirName)
        assertEquals('docs', convention.docsDirName)
        assertEquals('test-results', convention.testResultsDirName)
        assertEquals('tests', convention.testReportDirName)
    }

    @Test public void sourceCompatibilityDefaultsToCurentJvmVersion() {
        JavaVersion currentJvmVersion = JavaVersion.toVersion(System.properties["java.version"]);
        assertEquals(currentJvmVersion, convention.sourceCompatibility)
        assertEquals(currentJvmVersion, convention.targetCompatibility)
    }

    @Test public void canConfigureSourceSets() {
        File dir = new File('classes-dir')
        convention.sourceSets {
            main {
                output.classesDir = dir
            }
        }
        assertThat(convention.sourceSets.main.output.classesDir, equalTo(project.file(dir)))
    }
    
    @Test public void testDefaultDirs() {
        checkDirs()
    }

    @Test public void testDynamicDirs() {
        project.buildDir = project.file('mybuild')
        checkDirs()
    }

    private void checkDirs() {
        assertEquals(new File(project.buildDir, convention.dependencyCacheDirName), convention.dependencyCacheDir)
        assertEquals(new File(project.buildDir, convention.docsDirName), convention.docsDir)
        assertEquals(new File(project.buildDir, convention.testResultsDirName), convention.testResultsDir)
        assertEquals(new File(convention.reportsDir, convention.testReportDirName), convention.testReportDir)
    }

    @Test public void testTestReportDirIsCalculatedRelativeToReportsDir() {
        assertEquals(new File(project.buildDir, 'reports/tests'), convention.testReportDir)

        project.reportsDirName = 'other-reports-dir'
        convention.testReportDirName = 'other-test-dir'

        assertEquals(new File(project.buildDir, 'other-reports-dir/other-test-dir'), convention.testReportDir)
    }

    @Test public void testTargetCompatibilityDefaultsToSourceCompatibilityWhenNotSet() {
        convention.sourceCompatibility = '1.4'
        assertEquals(JavaVersion.VERSION_1_4, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_4, convention.targetCompatibility)

        convention.targetCompatibility = '1.2'
        assertEquals(JavaVersion.VERSION_1_4, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_2, convention.targetCompatibility)

        convention.sourceCompatibility = 6
        assertEquals(JavaVersion.VERSION_1_6, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_2, convention.targetCompatibility)
    }

    @Test
    public void createsManifestWithFileResolvingAndValues() {
        FileResolver fileResolver = context.mock(FileResolver)
        project.setFileResolver fileResolver
        TestFile manifestFile = expectPathResolved(fileResolver, 'file')
        manifestFile.write("key2: value2")
        def manifest = convention.manifest {
            from 'file'
            attributes(key1: 'value1')
        }
        assertThat(manifest, instanceOf(DefaultManifest.class))
        DefaultManifest mergedManifest = manifest.effectiveManifest
        assertThat(mergedManifest.attributes, equalTo([key1: 'value1', key2: 'value2', 'Manifest-Version': '1.0']))
    }

    @Test
    public void createsEmptyManifest() {
        assertThat(convention.manifest(), instanceOf(DefaultManifest.class))
    }

    private TestFile expectPathResolved(FileResolver fileResolver, String path) {
        TestFile file = tmpDir.file(path)
        context.checking {
            one(fileResolver).resolve(path)
            will(returnValue(file))
        }
        return file
    }
}
