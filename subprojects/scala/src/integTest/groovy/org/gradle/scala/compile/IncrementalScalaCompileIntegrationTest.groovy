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
package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.junit.Rule
import spock.lang.Issue

class IncrementalScalaCompileIntegrationTest extends AbstractIntegrationSpec {

    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def recompilesSourceWhenPropertiesChange() {
        expect:
        run('compileScala').assertTasksSkipped(':compileJava')

        when:
        file('build.gradle').text += '''
            compileScala.options.debug = false
'''
        then:
        run('compileScala').assertTasksSkipped(':compileJava')
        run('compileScala').assertTasksSkipped(':compileJava', ':compileScala')
    }

    def recompilesDependentClasses() {
        given:
        run("classes")

        when: // Update interface, compile should fail
        file('src/main/scala/IPerson.scala').assertIsFile().copyFrom(file('NewIPerson.scala'))

        then:
        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")
    }

    @Issue("GRADLE-2548")
    def recompilesScalaWhenJavaChanges() {
        file("build.gradle") << """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                compile 'org.scala-lang:scala-library:2.11.12'
            }
        """

        file("src/main/java/Person.java") << "public interface Person { String getName(); }"

        file("src/main/scala/DefaultPerson.scala") << """class DefaultPerson(name: String) extends Person {
    def getName(): String = name
}"""
        when:
        run('classes') //makes everything up-to-date

        //change the java interface
        file("src/main/java/Person.java").text = "public interface Person { String fooBar(); }"

        then:
        //the build should fail because the interface the scala class needs has changed
        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")
    }

}
