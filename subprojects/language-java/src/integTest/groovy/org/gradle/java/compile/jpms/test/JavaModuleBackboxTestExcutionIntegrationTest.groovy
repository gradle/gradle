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
import org.gradle.util.Requires
import org.gradle.util.TextUtil

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

    def "runs JUnit5 blackbox test as module using the module path"() {
        given:
        buildFile << """
            test { useJUnitPlatform() }
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.6.0'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine'
            }
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
    @Requires(adhoc = { AvailableJavaHomes.getJdk8() })
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
        failure.assertHasErrorOutput('Unrecognized option: --module')
    }
}
