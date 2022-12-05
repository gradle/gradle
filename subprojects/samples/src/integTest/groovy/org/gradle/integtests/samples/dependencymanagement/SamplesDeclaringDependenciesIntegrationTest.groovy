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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class SamplesDeclaringDependenciesIntegrationTest extends AbstractSampleIntegrationTest {

    private static final String COPY_LIBS_TASK_NAME = 'copyLibs'

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("dependencyManagement/declaringDependencies-concreteVersion")
    def "can use declare and resolve dependency with concrete version with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/libs/spring-web-5.0.2.RELEASE.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/declaringDependencies-withoutVersion")
    def "can use declare and resolve dependency without version with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/libs/spring-web-5.0.2.RELEASE.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/declaringDependencies-dynamicVersion")
    def "can use declare and resolve dependency with dynamic version with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyLibs')

        then:
        dslDir.file('build/libs').listFiles().any { it.name.startsWith('spring-web-5.') }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/declaringDependencies-changingVersion")
    def "can use declare and resolve dependency with changing version with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/libs/spring-web-5.0.3.BUILD-SNAPSHOT.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/declaringDependencies-fileDependencies")
    def "can use declare and resolve file dependencies with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        dslDir.file('build/libs/antcontrib.jar').isFile()
        dslDir.file('build/libs/commons-lang.jar').isFile()
        dslDir.file('build/libs/log4j.jar').isFile()
        dslDir.file('build/libs/a.exe').isFile()
        dslDir.file('build/libs/b.exe').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/declaringDependencies-artifactOnly")
    def "can resolve dependency with artifact-only declaration with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib(dslDir, 'jquery-3.2.1.js')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("dependencyManagement/declaringDependencies-artifactOnlyWithClassifier")
    def "can resolve dependency with artifact-only declaration with classifier with #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib(dslDir, 'jquery-3.2.1-min.js')

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
