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
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT
import static org.hamcrest.CoreMatchers.containsString

@Requires(KOTLIN_SCRIPT)
class SamplesScalaQuickstartIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider)
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, testDirectoryProvider)

    def setup() {
        executer.requireGradleDistribution()
    }

    @Unroll
    @UsesSample('scala/quickstart')
    def "can build jar with #dsl dsl"() {
        // Build and test projects
        TestFile projectDir = sample.dir.file(dsl)
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        // Check tests have run
        def result = new DefaultTestExecutionResult(projectDir)
        result.assertTestClassesExecuted('org.gradle.sample.PersonSpec')

        // Check contents of Jar
        TestFile jarContents = file('jar')
        projectDir.file("build/libs/quickstart.jar").unzipTo(jarContents)
        jarContents.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/sample/Named.class',
                'org/gradle/sample/Person.class'
        )

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('scala/quickstart')
    def "can build scalaDoc with #dsl dsl"() {
        if (GradleContextualExecuter.isDaemon()) {
            // don't load scala into the daemon as it exhausts permgen
            return
        }

        TestFile projectDir = sample.dir.file(dsl)
        executer.inDirectory(projectDir).withTasks('clean', 'scaladoc').run()

        projectDir.file('build/docs/scaladoc/index.html').assertExists()
        projectDir.file('build/docs/scaladoc/org/gradle/sample/Named.html')
            .assertContents(containsString("Defines the traits of one who is named."))

        where:
        dsl << ['groovy', 'kotlin']
    }
}
