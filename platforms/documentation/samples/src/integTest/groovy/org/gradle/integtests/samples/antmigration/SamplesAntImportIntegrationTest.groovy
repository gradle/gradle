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

package org.gradle.integtests.samples.antmigration

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

class SamplesAntImportIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("antMigration/importBuild")
    @ToBeFixedForConfigurationCache
    def "can import an Ant build and reconfigure its tasks (#dsl)"() {
        given: "A sample project with an Ant build"
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds('clean', 'assemble')

        then: "The correct JAR is built"
        dslDir.file('target/lib/hello-app.jar').isFile()

        and: "The compilejava task is executed in place of the original 'build' task"
        result.assertTaskExecuted(':compileJava')
        result.assertTaskNotExecuted(':build')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("antMigration/fileDeps")
    def "can copy file and flatDir dependencies (#dsl)"() {
        given: "A sample Java project with file and flatDir dependencies"
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('retrieveRuntimeDependencies')

        then: "The JARs are copied to the destination directory"
        dslDir.file('build/libs/our-custom.jar').isFile()
        dslDir.file('build/libs/awesome-framework-2.0.jar').isFile()
        dslDir.file('build/libs/utility-library-1.0.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("antMigration/fileDeps")
    def "can use task properties to link tasks (#dsl)"() {
        given: "A sample Java project"
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('javadocJar', 'unpackJavadocs')

        then: "The HTML Javadoc files are unpacked to the 'dist' directory"
        dslDir.file('build/dist/org/example/app/HelloApp.html').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("antMigration/multiProject")
    @ToBeFixedForConfigurationCache
    def "can link projects in a multi-project build via task dependencies (#dsl)"() {
        given: "A sample multi-project build"
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        def result = succeeds(':web:build')

        then: "The compile tasks are run in both 'util' and 'web' projects"
        result.assertTaskExecuted(':util:compile')
        result.assertTaskExecuted(':web:compile')

        where:
        dsl << ['groovy', 'kotlin']
    }
}
