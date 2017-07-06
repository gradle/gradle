/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires

import static org.gradle.api.JavaVersion.VERSION_1_7
import static org.gradle.api.JavaVersion.VERSION_1_8

class UpToDateScalaCompileIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file('src/main/scala/Person.scala') << "class Person(name: String)"
    }

    def "compile is out of date when changing the zinc version"() {
        def scalaVersion = '2.11.11'
        buildScript(scalaProjectBuildScript('0.3.13', scalaVersion))

        when:
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        when:
        run 'compileScala'

        then:
        skipped ':compileScala'

        when:
        buildScript(scalaProjectBuildScript('0.3.12', scalaVersion))
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'
    }

    def "compile is out of date when changing the scala version"() {
        def zincVersion = '0.3.13'
        buildScript(scalaProjectBuildScript(zincVersion, '2.11.11'))

        when:
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'

        when:
        run 'compileScala'

        then:
        skipped ':compileScala'

        when:
        buildScript(scalaProjectBuildScript(zincVersion, '2.11.9'))
        run 'compileScala'

        then:
        executedAndNotSkipped ':compileScala'
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(VERSION_1_7) && AvailableJavaHomes.getJdk(VERSION_1_8) })
    def "compile is out of date when changing the java version"() {
        def jdk7 = AvailableJavaHomes.getJdk(VERSION_1_7)
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)

        buildScript(scalaProjectBuildScript('0.3.13', '2.11.11'))
        when:
        executer.withJavaHome(jdk7.javaHome)
        run 'compileScala'

        then:
        executedAndNotSkipped(':compileScala')

        when:
        executer.withJavaHome(jdk7.javaHome)
        run 'compileScala'
        then:
        skipped ':compileScala'

        when:
        executer.withJavaHome(jdk8.javaHome)
        run 'compileScala', '--info'
        then:
        executedAndNotSkipped(':compileScala')
    }

    def scalaProjectBuildScript(String zincVersion, String scalaVersion) {
        return """
            apply plugin: 'scala'
                        
            repositories {
                jcenter()
            }

            dependencies {
                zinc "com.typesafe.zinc:zinc:${zincVersion}"
                compile "org.scala-lang:scala-library:${scalaVersion}" 
            }
            
            sourceCompatibility = '1.7'
            targetCompatibility = '1.7'
        """.stripIndent()
    }

}
