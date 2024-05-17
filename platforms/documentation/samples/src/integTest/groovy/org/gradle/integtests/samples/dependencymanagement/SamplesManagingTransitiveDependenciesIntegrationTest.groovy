/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.dependencymanagement

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class SamplesManagingTransitiveDependenciesIntegrationTest extends AbstractIntegrationSpec {

    private static final String COPY_LIBS_TASK_NAME = 'copyLibs'

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.withRepositoryMirrors()
    }

    @UsesSample("dependencyManagement/managingTransitiveDependencies-versionsWithConstraints")
    def "respects dependency constraints for direct and transitive dependencies with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/libs/httpclient-4.5.3.jar').isFile()
        dslDir.file('build/libs/commons-codec-1.11.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/managingTransitiveDependencies-forceForDependency")
    def "can force a dependency version for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.any { it.name == 'commons-codec-1.9.jar' }
        !libs.any { it.name == 'commons-codec-1.10.jar' }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/managingTransitiveDependencies-forceForConfiguration")
    def "can force a dependency version for particular configuration for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.any { it.name == 'commons-codec-1.9.jar' }
        !libs.any { it.name == 'commons-codec-1.10.jar' }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/managingTransitiveDependencies-disableForDependency")
    def "can disable transitive dependency resolution for dependency for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib(dslDir, 'guava-23.0.jar')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/managingTransitiveDependencies-disableForConfiguration")
    def "can disable transitive dependency resolution for particular configuration for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib(dslDir, 'guava-23.0.jar')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/managingTransitiveDependencies-constraintsFromBOM")
    def "can import dependency versions from a bom for #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        def libs = listFilesInBuildLibsDir(dslDir)
        libs.findAll { it.name == 'gson-2.8.2.jar' || it.name == 'dom4j-1.6.1.jar' || it.name == 'xml-apis-1.4.01.jar'}.size() == 3

        where:
        dsl << ['groovy', 'kotlin']
    }

    private TestFile[] listFilesInBuildLibsDir(TestFile dslDir) {
        dslDir.file('build/libs').listFiles()
    }

    private void assertSingleLib(TestFile dslDir, String filename) {
        def libs = listFilesInBuildLibsDir(dslDir)
        assert libs.size() == 1
        assert libs[0].name == filename
    }
}
