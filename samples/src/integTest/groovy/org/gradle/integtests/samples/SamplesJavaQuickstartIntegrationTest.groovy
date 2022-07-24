/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

import java.util.jar.Manifest

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class SamplesJavaQuickstartIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final Sample sample = new Sample(testDirectoryProvider, 'java/quickstart')

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "can build jar with #dsl dsl"() {
        given:
        TestFile javaprojectDir = sample.dir.file(dsl)

        when: "Build and test projects"
        executer.inDirectory(javaprojectDir).withTasks('clean', 'build').run()

        then: "Check tests have run"
        def result = new DefaultTestExecutionResult(javaprojectDir)
        result.assertTestClassesExecuted('org.gradle.PersonTest')
        // Check jar exists
        def jarFile = javaprojectDir.file("build/libs/quickstart-1.0.jar")
        jarFile.assertIsFile()
        // Check contents of Jar
        TestFile jarContents = file('jar')
        jarFile.unzipTo(jarContents)
        jarContents.assertHasDescendants(
            'META-INF/MANIFEST.MF',
            'org/gradle/Person.class',
            'org/gradle/resource.xml'
        )

        // Check contents of manifest
        Manifest manifest = jarContents.file('META-INF/MANIFEST.MF').withInputStream { new Manifest(it) }
        assertThat(manifest.mainAttributes.size(), equalTo(3))
        assertThat(manifest.mainAttributes.getValue('Manifest-Version'), equalTo('1.0'))
        assertThat(manifest.mainAttributes.getValue('Implementation-Title'), equalTo('Gradle Quickstart'))
        assertThat(manifest.mainAttributes.getValue('Implementation-Version'), equalTo('1.0'))

        where:
        dsl << ['groovy', 'kotlin']
    }
}
