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

package org.gradle.language.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.Requires
import spock.lang.Unroll

import static org.gradle.api.JavaVersion.VERSION_1_8
import static org.gradle.api.JavaVersion.VERSION_1_9

@Unroll
class UpToDatePlatformScalaCompileIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file('app/controller/Person.scala') << "class Person(name: String)"
    }

    def expectDeprecationWarnings() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The scala-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdk(VERSION_1_8) && AvailableJavaHomes.getJdk(VERSION_1_9) })
    @ToBeFixedForInstantExecution
    def "compile is out of date when changing the java version"() {
        def jdk8 = AvailableJavaHomes.getJdk(VERSION_1_8)
        def jdk9 = AvailableJavaHomes.getJdk(VERSION_1_9)

        def scalaFixture = new LanguageScalaCompilationFixture(temporaryFolder.testDirectory)
        scalaFixture.baseline()
        buildFile << scalaFixture.buildScript()
        when:
        expectDeprecationWarnings()
        executer.withJavaHome(jdk8.javaHome)
        run 'compileMainJarMainScala'

        then:
        executedAndNotSkipped(':compileMainJarMainScala')

        when:
        expectDeprecationWarnings()
        executer.withJavaHome(jdk8.javaHome)
        run 'compileMainJarMainScala'
        then:
        skipped ':compileMainJarMainScala'

        when:
        expectDeprecationWarnings()
        executer.withJavaHome(jdk9.javaHome)
        run 'compileMainJarMainScala'
        then:
        executedAndNotSkipped(':compileMainJarMainScala')
    }
}
