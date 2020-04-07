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

}
