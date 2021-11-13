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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class SamplesGroovyQuickstartIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    public final Sample sample = new Sample(testDirectoryProvider, 'groovy/quickstart')

    def "groovy project quickstart sample with #dsl dsl"() {
        given:
        TestFile groovyProjectDir = sample.dir.file(dsl)

        when:
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'build').run()

        then:
        // Check tests have run
        def result = new DefaultTestExecutionResult(groovyProjectDir)
        result.assertTestClassesExecuted('org.gradle.PersonTest')

        and:
        // Check contents of jar
        TestFile tmpDir = file('jarContents')
        groovyProjectDir.file('build/libs/quickstart.jar').unzipTo(tmpDir)
        tmpDir.assertHasDescendants(
            'META-INF/MANIFEST.MF',
            'org/gradle/Person.class',
            'org/gradle/Person$_closure1.class',
            'org/gradle/Person$_closure2.class',
            'resource.txt',
            'script.groovy'
        )

        where:
        dsl << ['groovy', 'kotlin']
    }
}
