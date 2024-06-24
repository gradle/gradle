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
import org.gradle.api.JavaVersion
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.DefaultManifest
import org.gradle.api.plugins.internal.DefaultJavaPluginConvention
import org.gradle.api.plugins.internal.DefaultJavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultJavaPluginConventionTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.create(tmpDir).rootProject()
    def sourceSets = Stub(SourceSetContainer)
    def toolchainSpec = TestUtil.newInstance(DefaultToolchainSpec)
    private JavaPluginExtension extension
    private JavaPluginConvention convention

    def setup() {
        project.pluginManager.apply(ReportingBasePlugin)
        extension = TestUtil.newInstance(DefaultJavaPluginExtension.class, project, sourceSets, toolchainSpec)
        convention = TestUtil.newInstance(DefaultJavaPluginConvention.class, project, extension)
    }

    def "default values"() {
        expect:
        convention.sourceSets.is(sourceSets)
        convention.docsDirName == 'docs'
        convention.testResultsDirName == 'test-results'
        convention.testReportDirName == 'tests'
    }

   def "source and target compatibility default to current jvm version"() {
        given:
        JavaVersion currentJvmVersion = JavaVersion.toVersion(System.properties["java.version"])
        expect:
        convention.sourceCompatibility == currentJvmVersion
        convention.targetCompatibility == currentJvmVersion
    }

    def 'source and target compatibility default to toolchain spec when it is configured'() {
        given:
        toolchainSpec.languageVersion.set(JavaLanguageVersion.of(14))

        expect:
        convention.sourceCompatibility == JavaVersion.VERSION_14
        convention.targetCompatibility == JavaVersion.VERSION_14

    }

    def "default dirs"() {
        expect:
        checkDirs()
    }

    def "dynamic dirs"() {
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

    def "targetCompatibility defaults to sourceCompatibility when not set"() {
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

    def "creates manifest with file resolving and values"() {
        given:
        project.file('file') << "key2: value2"
        def manifest = convention.manifest {
            from 'file'
            attributes(key1: 'value1')
        }

        when:
        def mergedManifest = manifest.effectiveManifest

        then:
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

    def "creates empty manifest"() {
        expect:
        convention.manifest() instanceof DefaultManifest
    }

}
