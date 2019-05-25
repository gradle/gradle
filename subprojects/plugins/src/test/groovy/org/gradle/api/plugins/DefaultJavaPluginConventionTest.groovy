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

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.internal.DefaultJavaPluginConvention
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.gradle.util.TestUtil
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat

class DefaultJavaPluginConventionTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def project = TestUtil.create(tmpDir).rootProject()
    def objectFactory = project.services.get(ObjectFactory)
    private JavaPluginConvention convention

    def setup() {
        project.pluginManager.apply(ReportingBasePlugin)
        convention = new DefaultJavaPluginConvention(project, objectFactory)
    }

    def defaultValues() {
        expect:
        convention.sourceSets instanceof DefaultSourceSetContainer
        convention.docsDirName == 'docs'
        convention.testResultsDirName == 'test-results'
        convention.testReportDirName == 'tests'
    }

   def sourceCompatibilityDefaultsToCurentJvmVersion() {
        given:
        JavaVersion currentJvmVersion = JavaVersion.toVersion(System.properties["java.version"]);
        expect:
        convention.sourceCompatibility == currentJvmVersion
        convention.targetCompatibility == currentJvmVersion
    }

    @Test
    void canConfigureSourceSets() {
        File dir = new File('classes-dir')
        convention.sourceSets {
            main {
                output.classesDir = dir
            }
        }
        assertThat(convention.sourceSets.main.output.classesDir, equalTo(project.file(dir)))
    }

    def defaultDirs() {
        expect:
        checkDirs()
    }

    def dynamicDirs() {
        when:
        project.buildDir = project.file('mybuild')
        then:
        checkDirs()
    }

    private void checkDirs() {
        assert convention.docsDir == new File(project.buildDir, convention.docsDirName)
        assert convention.testResultsDir == new File(project.buildDir, convention.testResultsDirName)
        assert convention.testReportDir == new File(convention.reportsDir, convention.testReportDirName)
    }

    def "testReportDir is calculated relative to reporting.baseDir"() {
        expect:
        convention.testReportDir == new File(project.buildDir, 'reports/tests')

        when:
        project.reporting.baseDir = 'other-reports-dir'
        convention.testReportDirName = 'other-test-dir'

        then:
        convention.testReportDir == new File(project.projectDir, 'other-reports-dir/other-test-dir')
    }

    def targetCompatibilityDefaultsToSourceCompatibilityWhenNotSet() {
        when:
        convention.sourceCompatibility = '1.4'
        then:
        convention.sourceCompatibility == JavaVersion.VERSION_1_4
        convention.targetCompatibility == JavaVersion.VERSION_1_4

        when:
        convention.targetCompatibility = '1.2'
        then:
        convention.sourceCompatibility == JavaVersion.VERSION_1_4
        convention.targetCompatibility == JavaVersion.VERSION_1_2

        when:
        convention.sourceCompatibility = 6
        then:
        convention.sourceCompatibility == JavaVersion.VERSION_1_6
        convention.targetCompatibility == JavaVersion.VERSION_1_2

        when:
        convention.targetCompatibility = JavaVersion.VERSION_1_3
        then:
        convention.sourceCompatibility == JavaVersion.VERSION_1_6
        convention.targetCompatibility == JavaVersion.VERSION_1_3

        when:
        convention.sourceCompatibility = JavaVersion.VERSION_1_7
        then:
        convention.sourceCompatibility == JavaVersion.VERSION_1_7
        convention.targetCompatibility == JavaVersion.VERSION_1_3
    }

    def createsManifestWithFileResolvingAndValues() {
        given:
        def fileResolver = Mock(FileResolver)
        def manifestFile = tmpDir.file('file') << "key2: value2"
        project.fileResolver = fileResolver
        def manifest = convention.manifest {
            from 'file'
            attributes(key1: 'value1')
        }

        when:
        def mergedManifest = manifest.effectiveManifest

        then:
        1 * fileResolver.resolve('file') >> manifestFile
        manifest instanceof DefaultManifest
        mergedManifest.attributes as Map == [key1: 'value1', key2: 'value2', 'Manifest-Version': '1.0']
    }

    def "can configure manifest with an Action"() {
        given:
        def manifest = convention.manifest({ Manifest manifest ->
            manifest.attributes key: 'value'
        } as Action<Manifest>)

        when:
        Manifest mergedManifest = manifest.effectiveManifest

        then:
        mergedManifest.attributes as Map == [key: 'value', 'Manifest-Version': '1.0']
    }

    def createsEmptyManifest() {
        expect:
        convention.manifest() instanceof DefaultManifest
    }

    def cannotCreateSourceSetWithEmptyName() {
        when:
        convention.sourceSets.create('')

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "The SourceSet name must not be empty."
    }

}
