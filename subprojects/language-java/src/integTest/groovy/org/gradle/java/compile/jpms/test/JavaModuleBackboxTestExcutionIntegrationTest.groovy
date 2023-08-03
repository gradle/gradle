/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.java.compile.jpms.test

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil

class JavaModuleBackboxTestExcutionIntegrationTest extends AbstractJavaModuleTestingIntegrationTest {

    def "runs JUnit4 blackbox test as module using the module path"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
        """

        when:
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires junit')
        testModuleClass('org.junit.Assert.assertEquals("consumer", consumer.MainModule.class.getModule().getName())')

        then:
        succeeds ':test'
    }

    // This test shows how to wire up the tests such that all modules run as Jars, so that resources form other folders than 'classes' are part of the corresponding module.
    // When we add more conveniences for setting up additional test sets, we should consider to make this the default setup (or add an option to set it up like this).
    def "can access resources in a blackbox test using the module path"() {
        given:
        buildFile << """
            dependencies {
                testImplementation 'junit:junit:4.13'
                testImplementation project(path) // depend on the main variant of the project
            }
            def testJarTask = tasks.register(sourceSets.test.jarTaskName, Jar) {
                archiveClassifier = 'tests'
                from sourceSets.test.output
            }
            test {
                // Make sure we run the 'Jar' containing the tests (and not just the 'classes' folder) so that test resources are also part of the test module
                classpath = configurations[sourceSets.test.runtimeClasspathConfigurationName] + files(testJarTask)
            }
        """

        when:
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires junit')
        file('src/test/resources/data.txt').text = "some data"
        testModuleClass('org.junit.Assert.assertNotNull("File data.txt not found!", this.getClass().getModule().getResourceAsStream("data.txt"))')

        then:
        succeeds ':test'
    }

    def "runs JUnit5 blackbox test as module using the module path"() {
        given:
        buildFile << """
            testing.suites.test.useJUnitJupiter()
        """

        when:
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires org.junit.jupiter.api')
        testModuleClass('org.junit.jupiter.api.Assertions.assertEquals("consumer", consumer.MainModule.class.getModule().getName())', 'org.junit.jupiter.api.Test')

        then:
        succeeds ':test'
    }


    def "runs TestNG blackbox test as module using the module path"() {
        given:
        buildFile << """
            test { useTestNG() }
            dependencies {
                testImplementation 'org.testng:testng:7.1.0'
            }
        """

        when:
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires org.testng')
        testModuleClass('org.testng.Assert.assertEquals("consumer", consumer.MainModule.class.getModule().getName())', 'org.testng.annotations.Test')

        then:
        succeeds ':test'
    }

    // This documents the current behavior.
    // In all places where we support Java Modules, we do not check if we actually run on Java 9 or later.
    // Instead, we just let javac/java/javadoc fail. We could improve by checking ourselves and throwing a different error.
    // But we should do that in all places then.
    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "fails testing a Java module on Java 8"() {
        given:
        buildFile << """
            dependencies { testImplementation 'junit:junit:4.13' }
            test {
                executable = '${TextUtil.escapeString(AvailableJavaHomes.getJdk8().javaExecutable)}'
            }
        """
        // Test workers that die very quickly after startup can cause an unexpected stack trace sometimes
        executer.withStackTraceChecksDisabled()

        when:
        testModuleInfo('requires junit')
        testModuleClass('')

        then:
        fails "test"
        // Oracle JDK or IBM JDK
        failure.assertHasErrorOutput('Unrecognized option: --module') || failure.assertHasErrorOutput('Command-line option unrecognised: --module')
    }

    def "runs JUnit4 module test on classpath if module path inference is turned off"() {
        given:
        buildFile << """
            tasks.test.modularity.inferModulePath = false
            dependencies { testImplementation 'junit:junit:4.13' }
        """

        when:
        consumingModuleInfo('exports consumer')
        consumingModuleClass()
        testModuleInfo('requires consumer', 'requires junit')
        testModuleClass('org.junit.Assert.assertNull(consumer.MainModule.class.getModule().getName())')

        then:
        succeeds ':test'
    }
}
