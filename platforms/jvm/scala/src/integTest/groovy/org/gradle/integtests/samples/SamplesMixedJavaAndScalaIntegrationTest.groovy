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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

import static org.hamcrest.CoreMatchers.containsString

@Requires(value = UnitTestPreconditions.Jdk23OrEarlier, reason = "Scala does not work with Java 24 without warnings yet")
class SamplesMixedJavaAndScalaIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'scala/mixedJavaAndScala')
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, testDirectoryProvider)

    def setup() {
        executer.withRepositoryMirrors()
    }

    def "can build jar"() {
        TestFile projectDir = sample.dir.file('groovy')

        // Build and test projects
        executer.inDirectory(projectDir).withTasks('clean', 'build').run()

        // Check tests have run
        def result = new DefaultTestExecutionResult(projectDir)
        result.assertTestClassesExecuted('org.gradle.sample.PersonSpec')

        // Check contents of Jar
        TestFile jarContents = file('jar')
        projectDir.file("build/libs/mixed-java-and-scala-1.0.jar").unzipTo(jarContents)
        jarContents.assertHasDescendants(
            'META-INF/MANIFEST.MF',
            'org/gradle/sample/JavaPerson.class',
            'org/gradle/sample/Named.class',
            'org/gradle/sample/Person.class',
            'org/gradle/sample/PersonList.class',
            'org/gradle/sample/PersonList$.class'
        )
    }

    def "can build docs"() {
        if (GradleContextualExecuter.isDaemon()) {
            // don't load scala into the daemon as it exhausts permgen
            return
        } else if (!GradleContextualExecuter.isEmbedded() && !GradleContextualExecuter.isParallel() && !JavaVersion.current().isJava8Compatible()) {
            executer.withBuildJvmOpts('-XX:MaxPermSize=128m')
        }

        TestFile projectDir = sample.dir.file('groovy')
        executer.inDirectory(projectDir).withTasks('clean', 'javadoc', 'scaladoc').run()

        TestFile javadocsDir = projectDir.file("build/docs/javadoc")
        javadocsDir.file("index.html").assertIsFile()
        javadocsDir.file("index.html").assertContents(containsString('mixed-java-and-scala 1.0 API'))
        javadocsDir.file("org/gradle/sample/JavaPerson.html").assertIsFile()
        javadocsDir.file("org/gradle/sample/Named.html").assertIsFile()

        TestFile scaladocsDir = projectDir.file("build/docs/scaladoc")
        scaladocsDir.file("index.html").assertIsFile()
        scaladocsDir.file("index.html").assertContents(containsString('mixed-java-and-scala 1.0 API'))
        scaladocsDir.file("org/gradle/sample/JavaPerson.html").assertIsFile()
        scaladocsDir.file("org/gradle/sample/Person.html").assertIsFile()
        scaladocsDir.file('org/gradle/sample/PersonList$.html').assertIsFile()
    }

}
