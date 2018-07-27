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

    @UsesSample("userguide/dependencyManagement/declaringDependencies/concreteVersion")
    def "can use declare and resolve dependency with concrete version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/libs/spring-web-5.0.2.RELEASE.jar').isFile()
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/withoutVersion")
    def "can use declare and resolve dependency without version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/libs/spring-web-5.0.2.RELEASE.jar').isFile()
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/dynamicVersion")
    def "can use declare and resolve dependency with dynamic version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyLibs')

        then:
        sample.dir.file('build/libs').listFiles().any { it.name.startsWith('spring-web-5.') }
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/changingVersion")
    def "can use declare and resolve dependency with changing version"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/libs/spring-web-5.0.3.BUILD-SNAPSHOT.jar').isFile()
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/fileDependencies")
    def "can use declare and resolve file dependencies"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        sample.dir.file('build/libs/antcontrib.jar').isFile()
        sample.dir.file('build/libs/commons-lang.jar').isFile()
        sample.dir.file('build/libs/log4j.jar').isFile()
        sample.dir.file('build/libs/a.exe').isFile()
        sample.dir.file('build/libs/b.exe').isFile()
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/projectDependencies")
    def "can declare and resolve project dependencies"() {
        executer.inDirectory(sample.dir)

        expect:
        succeeds('assemble')
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/artifactOnly")
    def "can resolve dependency with artifact-only declaration"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib('jquery-3.2.1.js')
    }

    @UsesSample("userguide/dependencyManagement/declaringDependencies/artifactOnlyWithClassifier")
    def "can resolve dependency with artifact-only declaration with classifier"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertSingleLib('jquery-3.2.1-min.js')
    }

    private TestFile[] listFilesInBuildLibsDir() {
        sample.dir.file('build/libs').listFiles()
    }

    private void assertSingleLib(String filename) {
        def libs = listFilesInBuildLibsDir()
        assert libs.size() == 1
        assert libs[0].name == filename
    }
}
