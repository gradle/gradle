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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue

@TargetVersions(["2.10.4", "2.11.1"])
class IncrementalScalaCompileIntegrationTest extends MultiVersionIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)

    def recompilesSourceWhenPropertiesChange() {
        expect:
        args("-i", "-PscalaVersion=$version")
        run('compileScala').assertTasksSkipped(':compileJava')

        when:
        file('build.gradle').text += '''
            compileScala.options.debug = false
'''
        then:
        args("-i", "-PscalaVersion=$version")
        run('compileScala').assertTasksSkipped(':compileJava')

        args("-i", "-PscalaVersion=$version")
        run('compileScala').assertTasksSkipped(':compileJava', ':compileScala')
    }

    def recompilesDependentClasses() {
        given:
        args("-i", "-PscalaVersion=$version")
        run("classes")

        when: // Update interface, compile should fail
        file('src/main/scala/IPerson.scala').assertIsFile().copyFrom(file('NewIPerson.scala'))

        then:
        args("-i", "-PscalaVersion=$version")
        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")
    }

    @Issue("GRADLE-2548")
    @Ignore
    def recompilesScalaWhenJavaChanges() {
        file("build.gradle") << """
            apply plugin: 'scala'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.scala-lang:scala-library:$version'
            }
        """

        file("src/main/java/Person.java") << "public interface Person { String getName(); }"

        file("src/main/scala/DefaultPerson.scala") << """class DefaultPerson(name: String) extends Person {
    def getName(): String = name
}"""
        when:
        args("-i", "-PscalaVersion=$version")
        run('classes') //makes everything up-to-date

        //change the java interface
        file("src/main/java/Person.java").text = "public interface Person { String fooBar(); }"

        then:
        args("-i", "-PscalaVersion=$version")
        //the build should fail because the interface the scala class needs has changed
        runAndFail("classes").assertHasDescription("Execution failed for task ':compileScala'.")
    }
}
